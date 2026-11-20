package com.novacart.mall.service;

/** Raised when concurrent payment callbacks share the same callback ID. */
public class DuplicatePaymentCallbackException extends RuntimeException {
    private final String callbackId;

    public DuplicatePaymentCallbackException(String callbackId) {
        super("Duplicate payment callback");
        this.callbackId = callbackId;
    }

    public String getCallbackId() {
        return callbackId;
    }
}
