package com.novacart.mall.dto;

import java.math.BigDecimal;
import java.util.List;

/** Cart view returned to the web client and AI tools. */
public record CartSummary(
        List<CartLine> items,
        BigDecimal totalAmount,
        String recipient,
        String phone
) {
}
