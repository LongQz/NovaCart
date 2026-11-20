package com.novacart.mall.service;

/** Raised when two concurrent requests use the same order idempotency key. */
public class DuplicateOrderRequestException extends RuntimeException {
    private final String requestKey;

    public DuplicateOrderRequestException(String requestKey) {
        super("Duplicate order request");
        this.requestKey = requestKey;
    }

    public String getRequestKey() {
        return requestKey;
    }
}
