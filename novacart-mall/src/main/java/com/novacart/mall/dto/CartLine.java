package com.novacart.mall.dto;

import java.math.BigDecimal;

/** Cart line enriched with catalog data. */
public record CartLine(
        Long productId,
        String name,
        String imageUrl,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal
) {
}
