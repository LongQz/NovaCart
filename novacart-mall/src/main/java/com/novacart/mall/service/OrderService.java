package com.novacart.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.api.DeliveryClient;
import com.novacart.common.BizType;
import com.novacart.common.CartItem;
import com.novacart.common.DeliveryTaskRequest;
import com.novacart.common.OrderCreateRequest;
import com.novacart.common.OrderStatus;
import com.novacart.mall.dto.OrderView;
import com.novacart.mall.dto.PaymentCallbackRequest;
import com.novacart.mall.entity.OrderItem;
import com.novacart.mall.entity.Orders;
import com.novacart.mall.entity.PaymentCallbackLog;
import com.novacart.mall.entity.Product;
import com.novacart.mall.mapper.OrderItemMapper;
import com.novacart.mall.mapper.OrderMapper;
import com.novacart.mall.mapper.PaymentCallbackMapper;
import com.novacart.mall.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Transactional checkout workflow for both mall and instant delivery. */
@Service
public class OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final CartService cartService;
    private final DeliveryClient deliveryClient;
    private final OrderEventPublisher eventPublisher;
    private final RedisStockService redisStockService;
    private final PaymentCallbackMapper paymentCallbackMapper;

    public OrderService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            ProductMapper productMapper,
            ProductService productService,
            CartService cartService,
            DeliveryClient deliveryClient,
            OrderEventPublisher eventPublisher,
            RedisStockService redisStockService,
            PaymentCallbackMapper paymentCallbackMapper
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productService = productService;
        this.cartService = cartService;
        this.deliveryClient = deliveryClient;
        this.eventPublisher = eventPublisher;
        this.redisStockService = redisStockService;
        this.paymentCallbackMapper = paymentCallbackMapper;
    }

    @Transactional
    public OrderView create(OrderCreateRequest request) {
        // Idempotency is checked in the database before Redis reservation.
        validate(request);
        String requestKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? UUID.randomUUID().toString()
                : request.idempotencyKey();
        Orders existing = orderMapper.findByRequestKey(requestKey);
        if (existing != null) {
            return view(existing);
        }

        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : request.items()) {
            Product product = productService.requireProduct(item.productId());
            if (!request.bizType().name().equals(product.getBizType())) {
                throw new BizException("商城商品和闪购商品需要分别下单");
            }
            products.put(product.getId(), product);
            quantities.merge(product.getId(), item.quantity(), Integer::sum);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }

        products.values().forEach(redisStockService::load);
        redisStockService.reserve(quantities);

        try {
            quantities.forEach((productId, quantity) -> deductWithOptimisticRetry(products.get(productId), quantity));

            for (CartItem item : request.items()) {
                productMapper.increaseSales(item.productId(), item.quantity());
            }

            Orders order = buildOrder(request, requestKey, total);
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException exception) {
                throw new DuplicateOrderRequestException(requestKey);
            }

            for (CartItem item : request.items()) {
                Product product = products.get(item.productId());
                OrderItem line = new OrderItem();
                line.setOrderId(order.getId());
                line.setProductId(product.getId());
                line.setProductName(product.getName());
                line.setPrice(product.getPrice());
                line.setQuantity(item.quantity());
                orderItemMapper.insert(line);
            }
            cartService.clear(request.userId());
            eventPublisher.orderTimeout(order);
            return view(order);
        } catch (RuntimeException exception) {
            // Database changes roll back with the transaction; Redis must be
            // compensated explicitly when any downstream step fails.
            redisStockService.restore(quantities);
            throw exception;
        }
    }

    private Orders buildOrder(OrderCreateRequest request, String requestKey, BigDecimal total) {
        Orders order = new Orders();
        order.setOrderNo("NC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setRequestKey(requestKey);
        order.setUserId(request.userId());
        order.setBizType(request.bizType().name());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setTotalAmount(total);
        order.setRecipient(request.recipient());
        order.setPhone(request.phone());
        order.setAddress(request.address());
        order.setNote(request.note());
        order.setCreatedAt(LocalDateTime.now());
        order.setVersion(0);
        return order;
    }

    private void deductWithOptimisticRetry(Product product, int quantity) {
        Product latest = product;
        for (int attempt = 0; attempt < 3; attempt++) {
            int version = latest.getVersion() == null ? 0 : latest.getVersion();
            if (productMapper.deductStockWithVersion(product.getId(), quantity, version) == 1) {
                return;
            }
            latest = productService.requireProduct(product.getId());
            if (latest.getStock() < quantity) {
                break;
            }
        }
        // Under heavy contention, the conditional stock update is the final
        // database guard. The version checks above avoid lost updates when
        // contention is low, while this branch prevents unnecessary failures.
        if (productMapper.deductStock(product.getId(), quantity) == 1) {
            return;
        }
        throw new BizException(product.getName() + " 库存不足或并发更新冲突");
    }

    @GlobalTransactional(name = "novacart-pay-order", rollbackFor = Exception.class)
    @Transactional
    public OrderView pay(Long orderId) {
        return handlePaymentCallback(new PaymentCallbackRequest(
                "manual-" + orderId + "-" + UUID.randomUUID(),
                orderId,
                true
        ));
    }

    @GlobalTransactional(name = "novacart-payment-callback", rollbackFor = Exception.class)
    @Transactional
    public OrderView handlePaymentCallback(PaymentCallbackRequest callback) {
        PaymentCallbackLog callbackLog = new PaymentCallbackLog();
        callbackLog.setCallbackId(callback.callbackId());
        callbackLog.setOrderId(callback.orderId());
        callbackLog.setPayload(callback.callbackId() + ":" + callback.success());
        try {
            paymentCallbackMapper.insert(callbackLog);
        } catch (DuplicateKeyException exception) {
            throw new DuplicatePaymentCallbackException(callback.callbackId());
        }

        Orders order = orderMapper.selectById(callback.orderId());
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (callback.success() && OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            order.setStatus(BizType.DELIVERY.name().equals(order.getBizType())
                    ? OrderStatus.PENDING_MERCHANT.name()
                    : OrderStatus.PAID.name());
            order.setPaidAt(LocalDateTime.now());
            if (orderMapper.updateById(order) != 1) {
                Orders latest = orderMapper.selectById(callback.orderId());
                if (latest == null || OrderStatus.PENDING_PAYMENT.name().equals(latest.getStatus())) {
                    throw new BizException("支付状态并发更新失败");
                }
                return view(latest);
            }
            if (BizType.DELIVERY.name().equals(order.getBizType())) {
                try {
                    deliveryClient.createTask(new DeliveryTaskRequest(
                            order.getId(), order.getUserId(), order.getRecipient(), order.getPhone(), order.getAddress()));
                } catch (Exception exception) {
                    LOGGER.warn("Delivery task creation deferred: {}", exception.getMessage());
                }
            }
            eventPublisher.orderPaid(order);
        }
        return view(order);
    }

    public OrderView findByIdempotencyKey(String requestKey) {
        Orders order = orderMapper.findByRequestKey(requestKey);
        if (order == null) {
            throw new BizException("幂等订单不存在");
        }
        return view(order);
    }

    public OrderView findByCallbackId(String callbackId) {
        PaymentCallbackLog callback = paymentCallbackMapper.findByCallbackId(callbackId);
        if (callback == null) {
            throw new BizException("支付回调不存在");
        }
        Orders order = orderMapper.selectById(callback.getOrderId());
        if (order == null) {
            throw new BizException("回调关联订单不存在");
        }
        return view(order);
    }

    @GlobalTransactional(name = "novacart-cancel-order", rollbackFor = Exception.class)
    @Transactional
    public OrderView cancel(Long orderId, Long userId, String reason) {
        // 用户只能取消自己的订单；外卖已发货时必须拒绝取消。
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        if (!List.of(
                OrderStatus.PENDING_PAYMENT.name(),
                OrderStatus.PAID.name(),
                OrderStatus.PENDING_MERCHANT.name(),
                OrderStatus.PENDING_SHIPMENT.name()
        ).contains(order.getStatus())) {
            throw new BizException("订单当前状态不允许取消");
        }
        if (BizType.DELIVERY.name().equals(order.getBizType())) {
            var taskResponse = deliveryClient.getTask(order.getId());
            String deliveryStatus = taskResponse.data() == null ? null : taskResponse.data().status().name();
            if (deliveryStatus != null && List.of(
                    "SHIPPED", "DELIVERING", "ARRIVED"
            ).contains(deliveryStatus)) {
                throw new BizException("商家已发货，订单不能取消");
            }
            try {
                deliveryClient.cancel(order.getId());
            } catch (Exception ignored) {
                // The local cancellation still restores inventory if no delivery task exists yet.
            }
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelReason(reason == null || reason.isBlank() ? "用户取消" : reason);
        if (orderMapper.updateById(order) != 1) {
            throw new BizException("订单状态已变化，请刷新后重试");
        }
        restoreOrderInventory(orderId);
        return view(order);
    }

    @Transactional
    public void cancelTimedOutOrder(Long orderId) {
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelReason("超时未支付");
        if (orderMapper.updateById(order) != 1) {
            return;
        }
        restoreOrderInventory(orderId);
    }

    public OrderView get(Long orderId) {
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return view(order);
    }

    /**
     * 查询当前用户自己的订单。
     *
     * <p>订单不存在和无权查看统一返回“订单不存在”，避免通过错误信息判断
     * 其他用户的订单号是否存在。</p>
     */
    public OrderView getOwned(Long orderId, Long userId) {
        Orders order = orderMapper.selectById(orderId);
        if (order == null || userId == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        return view(order);
    }

    public List<Orders> list(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .orderByDesc(Orders::getId));
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredOrders() {
        // Database scan remains a safety net if RabbitMQ is unavailable.
        for (Orders order : orderMapper.findExpiredOrders()) {
            cancelTimedOutOrder(order.getId());
        }
    }

    private void validate(OrderCreateRequest request) {
        if (request == null || request.userId() == null || request.bizType() == null) {
            throw new BizException("订单参数不完整");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BizException("订单商品不能为空");
        }
        if (request.items().stream().anyMatch(item -> item.quantity() <= 0)) {
            throw new BizException("商品数量必须大于 0");
        }
        if (request.bizType() == BizType.DELIVERY
                && (request.address() == null || request.address().isBlank()
                || request.recipient() == null || request.recipient().isBlank()
                || request.phone() == null || request.phone().isBlank())) {
            throw new BizException("闪购订单必须填写收货人、电话和配送地址");
        }
    }

    private OrderView view(Orders order) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        return new OrderView(order, items, null);
    }

    private void restoreOrderInventory(Long orderId) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId))
                .forEach(item -> quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum));
        quantities.forEach((productId, quantity) -> productMapper.restoreStock(productId, quantity));
        redisStockService.restore(quantities);
    }
}
