package com.novacart.common;

/** RabbitMQ delayed event used to cancel unpaid orders. */
public record OrderTimeoutEvent(Long orderId, Long userId) {
}
