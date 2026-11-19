package com.novacart.common;

/**
 * Cart line stored in Redis.
 *
 * @author qzh
 */
public record CartItem(Long productId, int quantity) {
}
