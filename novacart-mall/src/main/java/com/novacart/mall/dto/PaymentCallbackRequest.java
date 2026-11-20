package com.novacart.mall.dto;

/** External payment provider callback payload. */
public record PaymentCallbackRequest(
        String callbackId,
        Long orderId,
        boolean success
) {
}
