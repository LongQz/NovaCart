package com.novacart.ai.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模块与业务系统之间使用的稳定数据契约。
 *
 * <p>这里不直接引用商城实体，避免 AI 模块反向依赖商城模块。</p>
 */
public final class AiContracts {
    private AiContracts() {
    }

    public record User(Long id, String username, String nickname, String role) {
    }

    public record Product(
            Long id,
            String name,
            String category,
            String bizType,
            BigDecimal price,
            Integer stock,
            Integer sales,
            String description,
            String tags,
            String imageUrl,
            Boolean active
    ) {
    }

    public record CartLine(
            Long productId,
            String name,
            String imageUrl,
            BigDecimal price,
            Integer quantity,
            BigDecimal subtotal
    ) {
    }

    public record CartSummary(
            List<CartLine> items,
            BigDecimal totalAmount,
            String recipient,
            String phone
    ) {
    }

    public record Order(
            Long id,
            String orderNo,
            String bizType,
            String status,
            BigDecimal totalAmount,
            String recipient,
            String phone,
            String address,
            LocalDateTime createdAt
    ) {
    }

    public record Delivery(
            Long orderId,
            String status,
            String courierName,
            Integer etaMinutes,
            LocalDateTime updatedAt
    ) {
    }
}
