package com.novacart.mall.listener;

import com.novacart.common.OrderTimeoutEvent;
import com.novacart.mall.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes dead-lettered timeout events and closes still-unpaid orders. */
@Component
public class OrderTimeoutListener {
    private final OrderService orderService;

    public OrderTimeoutListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "novacart.order.timeout")
    public void onOrderTimeout(OrderTimeoutEvent event) {
        orderService.cancelTimedOutOrder(event.orderId());
    }
}
