package com.novacart.mall.service;

import com.novacart.ai.gateway.AiCommerceGateway;
import com.novacart.ai.gateway.AiContracts;
import com.novacart.api.DeliveryClient;
import com.novacart.mall.dto.AuthUser;
import com.novacart.mall.dto.CartSummary;
import com.novacart.mall.dto.OrderView;
import com.novacart.mall.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 商城业务对统一 AI 端口的适配器。
 *
 * <p>AI 模块不引用商城 Service。该适配器把商品、购物车、订单、配送和用户身份
 * 转换成 AI 模块自己的稳定数据契约。</p>
 */
@Component
public class MallAiCommerceGateway implements AiCommerceGateway {
    private final AuthService authService;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final MapEstimateService mapEstimateService;
    private final DeliveryClient deliveryClient;

    public MallAiCommerceGateway(
            AuthService authService,
            ProductService productService,
            CartService cartService,
            OrderService orderService,
            MapEstimateService mapEstimateService,
            DeliveryClient deliveryClient
    ) {
        this.authService = authService;
        this.productService = productService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.mapEstimateService = mapEstimateService;
        this.deliveryClient = deliveryClient;
    }

    @Override
    public AiContracts.User requireUser(String authorization) {
        AuthUser user = authService.requireUser(authorization);
        return new AiContracts.User(user.id(), user.username(), user.nickname(), user.role());
    }

    @Override
    public List<AiContracts.Product> searchProducts(String bizType, String keyword) {
        return productService.search(bizType, keyword).stream().map(this::product).toList();
    }

    @Override
    public List<AiContracts.Product> requireProducts(List<Long> productIds) {
        return productIds.stream().map(productService::requireProduct).map(this::product).toList();
    }

    @Override
    public AiContracts.CartSummary cartSummary(Long userId) {
        CartSummary cart = cartService.getCart(userId);
        return new AiContracts.CartSummary(
                cart.items().stream()
                        .map(line -> new AiContracts.CartLine(
                                line.productId(),
                                line.name(),
                                line.imageUrl(),
                                line.price(),
                                line.quantity(),
                                line.subtotal()))
                        .toList(),
                cart.totalAmount(),
                cart.recipient(),
                cart.phone()
        );
    }

    @Override
    public Optional<AiContracts.Order> orderStatus(Long orderId, Long userId) {
        try {
            OrderView view = orderService.getOwned(orderId, userId);
            return Optional.of(new AiContracts.Order(
                    view.order().getId(),
                    view.order().getOrderNo(),
                    view.order().getBizType(),
                    view.order().getStatus(),
                    view.order().getTotalAmount(),
                    view.order().getRecipient(),
                    view.order().getPhone(),
                    view.order().getAddress(),
                    view.order().getCreatedAt()
            ));
        } catch (BizException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AiContracts.Delivery> deliveryStatus(Long orderId, Long userId) {
        AiContracts.Order order = orderStatus(orderId, userId).orElse(null);
        if (order == null || !"DELIVERY".equals(order.bizType())) {
            return Optional.empty();
        }
        var response = deliveryClient.getTask(orderId);
        if (!response.success() || response.data() == null) {
            return Optional.empty();
        }
        var task = response.data();
        return Optional.of(new AiContracts.Delivery(
                task.orderId(),
                task.status().name(),
                task.courierName(),
                task.etaMinutes(),
                task.updatedAt()
        ));
    }

    @Override
    public int estimateDeliveryMinutes(String address) {
        return mapEstimateService.estimateMinutes(address);
    }

    private AiContracts.Product product(Product product) {
        return new AiContracts.Product(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getBizType(),
                product.getPrice(),
                product.getStock(),
                product.getSales(),
                product.getDescription(),
                product.getTags(),
                product.getImageUrl(),
                product.getActive()
        );
    }
}
