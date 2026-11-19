package com.novacart.delivery.listener;

import com.novacart.common.DeliveryTaskRequest;
import com.novacart.common.OrderPaidEvent;
import com.novacart.delivery.service.DeliveryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Idempotently consumes paid orders; Feign and RabbitMQ can safely race. */
@Component
public class OrderPaidListener {
    private final DeliveryService deliveryService;

    public OrderPaidListener(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @RabbitListener(queues = "novacart.order.paid")
    public void onOrderPaid(OrderPaidEvent event) {
        deliveryService.createTask(new DeliveryTaskRequest(
                event.orderId(), event.userId(), event.recipient(), event.phone(), event.address()));
    }
}
