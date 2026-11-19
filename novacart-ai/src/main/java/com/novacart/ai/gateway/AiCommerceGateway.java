package com.novacart.ai.gateway;

import java.util.List;
import java.util.Optional;

/**
 * AI 模块访问业务能力的统一端口。
 *
 * <p>该接口由具体业务服务实现。AI 工具只依赖这里的方法，不直接依赖商城
 * Service、MyBatis Mapper 或配送 Controller。</p>
 */
public interface AiCommerceGateway {
    AiContracts.User requireUser(String authorization);

    List<AiContracts.Product> searchProducts(String bizType, String keyword);

    List<AiContracts.Product> requireProducts(List<Long> productIds);

    AiContracts.CartSummary cartSummary(Long userId);

    Optional<AiContracts.Order> orderStatus(Long orderId, Long userId);

    Optional<AiContracts.Delivery> deliveryStatus(Long orderId, Long userId);

    int estimateDeliveryMinutes(String address);
}
