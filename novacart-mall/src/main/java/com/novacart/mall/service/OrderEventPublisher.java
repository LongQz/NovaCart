package com.novacart.mall.service;

import com.novacart.common.OrderPaidEvent;
import com.novacart.common.OrderTimeoutEvent;
import com.novacart.mall.entity.Orders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publishes best-effort order events without making RabbitMQ critical to checkout. */
@Component
public class OrderEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void orderPaid(Orders order) {
        if (!"DELIVERY".equals(order.getBizType())) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    "novacart.order.events",
                    "order.paid",
                    new OrderPaidEvent(
                            order.getId(), order.getUserId(), order.getRecipient(), order.getPhone(), order.getAddress())
            );
        } catch (Exception exception) {
            LOGGER.warn("RabbitMQ order event skipped: {}", exception.getMessage());
        }
    }

    public void orderTimeout(Orders order) {
        try {
            rabbitTemplate.convertAndSend(
                    "novacart.order.timeout.delay",
                    "order.timeout.wait",
                    new OrderTimeoutEvent(order.getId(), order.getUserId())
            );
        } catch (Exception exception) {
            LOGGER.warn("RabbitMQ timeout event skipped: {}", exception.getMessage());
        }
    }
}
