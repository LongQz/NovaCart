package com.novacart.common;

import java.io.Serializable;

/** RabbitMQ event published after a NovaCart order is paid. */
public record OrderPaidEvent(
        Long orderId,
        Long userId,
        String recipient,
        String phone,
        String address
) implements Serializable {
}
