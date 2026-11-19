package com.novacart.common;

/** Order lifecycle. */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PENDING_MERCHANT,
    PENDING_SHIPMENT,
    SHIPPED,
    DELIVERING,
    ARRIVED,
    REVIEWED,
    CANCELLED,
    COMPLETED
}
