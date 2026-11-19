package com.novacart.api;

import com.novacart.common.ApiResponse;
import com.novacart.common.DeliveryTaskRequest;
import com.novacart.common.DeliveryTaskView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商城与配送服务之间的 OpenFeign 契约。
 *
 * <p>该接口只供服务端同步调用使用；RabbitMQ 消费者负责另一条异步建单路径，
 * 两条路径都依赖 orderId 做幂等。</p>
 *
 * @author qzh
 */
@FeignClient(name = "novacart-delivery", url = "${novacart.delivery.url:}")
public interface DeliveryClient {
    @PostMapping("/internal/delivery/tasks")
    ApiResponse<DeliveryTaskView> createTask(@RequestBody DeliveryTaskRequest request);

    @PostMapping("/internal/delivery/tasks/{orderId}/dispatch")
    ApiResponse<DeliveryTaskView> dispatch(@PathVariable("orderId") Long orderId);

    @GetMapping("/internal/delivery/tasks/{orderId}")
    ApiResponse<DeliveryTaskView> getTask(@PathVariable("orderId") Long orderId);

    @PostMapping("/internal/delivery/tasks/{orderId}/cancel")
    ApiResponse<DeliveryTaskView> cancel(@PathVariable("orderId") Long orderId);

    @PostMapping("/internal/delivery/tasks/{orderId}/advance")
    ApiResponse<DeliveryTaskView> advance(@PathVariable("orderId") Long orderId);
}
