package com.novacart.common;

/**
 * Internal delivery task request sent through OpenFeign.
 *
 * @author qzh
 */
public record DeliveryTaskRequest(Long orderId, Long userId, String recipient, String phone, String address) {
}
