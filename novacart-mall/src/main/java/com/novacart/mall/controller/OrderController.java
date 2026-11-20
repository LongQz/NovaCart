package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.common.OrderCreateRequest;
import com.novacart.mall.dto.OrderView;
import com.novacart.mall.dto.PaymentCallbackRequest;
import com.novacart.mall.entity.Orders;
import com.novacart.mall.service.DuplicateOrderRequestException;
import com.novacart.mall.service.DuplicatePaymentCallbackException;
import com.novacart.mall.service.OrderService;
import com.novacart.mall.dto.AuthUser;
import com.novacart.mall.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Checkout, payment and order history endpoints. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final AuthService authService;

    public OrderController(OrderService orderService, AuthService authService) {
        this.orderService = orderService;
        this.authService = authService;
    }

    @PostMapping
    public ApiResponse<OrderView> create(
            @RequestHeader("Authorization") String authorization,
            @RequestBody OrderCreateRequest request
    ) {
        // 服务端覆盖请求体中的 userId，防止替其他用户下单。
        AuthUser user = authService.requireUser(authorization);
        try {
            return ApiResponse.ok(orderService.create(new OrderCreateRequest(
                    user.id(),
                    request.bizType(),
                    request.recipient(),
                    request.phone(),
                    request.address(),
                    request.note(),
                    request.idempotencyKey(),
                    request.items()
            )));
        } catch (DuplicateOrderRequestException exception) {
            return ApiResponse.ok(orderService.findByIdempotencyKey(exception.getRequestKey()));
        }
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderView> pay(@PathVariable Long id) {
        // 本地模拟支付；外卖支付后会通过 Feign 和 RabbitMQ 触发配送。
        return ApiResponse.ok(orderService.pay(id));
    }

    @PostMapping("/payment-callback")
    public ApiResponse<OrderView> paymentCallback(@RequestBody PaymentCallbackRequest request) {
        try {
            return ApiResponse.ok(orderService.handlePaymentCallback(request));
        } catch (DuplicatePaymentCallbackException exception) {
            return ApiResponse.ok(orderService.findByCallbackId(exception.getCallbackId()));
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> get(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        Long userId = authService.requireUser(authorization).id();
        OrderView view = orderService.get(id);
        if (!view.order().getUserId().equals(userId)) {
            throw new com.novacart.mall.service.UnauthorizedException("无权查看该订单");
        }
        return ApiResponse.ok(view);
    }

    @GetMapping
    public ApiResponse<List<Orders>> list(
            @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.ok(orderService.list(authService.requireUser(authorization).id()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderView> cancel(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> request
    ) {
        // 取消规则由 OrderService 校验，包括外卖是否已经发货。
        Long userId = authService.requireUser(authorization).id();
        String reason = request == null ? "用户取消" : request.get("reason");
        return ApiResponse.ok(orderService.cancel(id, userId, reason));
    }
}
