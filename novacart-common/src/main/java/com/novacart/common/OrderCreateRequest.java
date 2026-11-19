package com.novacart.common;

import java.util.List;

/**
 * Create-order request shared by web and API clients.
 *
 * @author qzh
 */
public record OrderCreateRequest(
        Long userId,
        BizType bizType,
        String recipient,
        String phone,
        String address,
        String note,
        String idempotencyKey,
        List<CartItem> items
) {
}
