package com.novacart.common;

import java.time.LocalDateTime;

/**
 * Delivery task returned to the mall service and web client.
 *
 * @author qzh
 */
public record DeliveryTaskView(
        Long id,
        Long orderId,
        Long userId,
        String recipient,
        String phone,
        String address,
        String courierName,
        DeliveryStatus status,
        Integer etaMinutes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
