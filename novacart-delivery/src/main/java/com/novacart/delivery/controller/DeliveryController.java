package com.novacart.delivery.controller;

import com.novacart.common.ApiResponse;
import com.novacart.common.DeliveryTaskRequest;
import com.novacart.common.DeliveryTaskView;
import com.novacart.delivery.service.DeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配送任务接口。
 *
 * <p>/internal 路径供 OpenFeign 和运营端调用；/api 路径供用户页面读取配送进度。
 * 物流状态推进逻辑集中在 {@link DeliveryService}，控制器不直接改数据库。</p>
 */
@RestController
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/internal/delivery/tasks")
    public ApiResponse<DeliveryTaskView> create(@RequestBody DeliveryTaskRequest request) {
        // 仅由商城服务通过 OpenFeign 或 RabbitMQ 消费者调用。
        return ApiResponse.ok(deliveryService.createTask(request));
    }

    @PostMapping("/internal/delivery/tasks/{orderId}/dispatch")
    public ApiResponse<DeliveryTaskView> dispatch(@PathVariable Long orderId) {
        return ApiResponse.ok(deliveryService.dispatch(orderId));
    }

    @GetMapping("/api/delivery/tasks/{orderId}")
    public ApiResponse<DeliveryTaskView> get(@PathVariable Long orderId) {
        // 用户订单页读取当前配送节点、骑手和预计送达时间。
        return ApiResponse.ok(deliveryService.get(orderId));
    }

    @GetMapping("/internal/delivery/tasks/{orderId}")
    public ApiResponse<DeliveryTaskView> internalGet(@PathVariable Long orderId) {
        return ApiResponse.ok(deliveryService.get(orderId));
    }

    @PostMapping("/internal/delivery/tasks/{orderId}/cancel")
    public ApiResponse<DeliveryTaskView> cancel(@PathVariable Long orderId) {
        return ApiResponse.ok(deliveryService.cancel(orderId));
    }

    @PostMapping("/internal/delivery/tasks/{orderId}/advance")
    public ApiResponse<DeliveryTaskView> internalAdvance(@PathVariable Long orderId) {
        // 开发者端推进物流状态。
        return ApiResponse.ok(deliveryService.advance(orderId));
    }

    @PostMapping("/api/delivery/tasks/{orderId}/advance")
    public ApiResponse<DeliveryTaskView> advance(@PathVariable Long orderId) {
        // 保留公开演示入口，正式系统可替换为 ADMIN Token 校验。
        return ApiResponse.ok(deliveryService.advance(orderId));
    }

    @GetMapping("/api/delivery/tasks")
    public ApiResponse<List<DeliveryTaskView>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId
    ) {
        return ApiResponse.ok(deliveryService.list(userId));
    }
}
