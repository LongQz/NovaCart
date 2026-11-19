package com.novacart.common;

/** Delivery task lifecycle. */
public enum DeliveryStatus {
    PENDING_MERCHANT,
    PENDING_SHIPMENT,
    SHIPPED,
    DELIVERING,
    ARRIVED,
    CANCELLED
}
