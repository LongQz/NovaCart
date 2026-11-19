package com.novacart.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novacart.ai.gateway.AiCommerceGateway;
import com.novacart.ai.gateway.AiContracts;
import com.novacart.ai.rag.CatalogRagService;
import com.novacart.ai.service.AiBusinessException;
import com.novacart.common.BizType;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 暴露给 Spring AI 的只读业务工具。
 *
 * <p>工具方法中的 {@link ToolParam} 由模型填写，只能作为查询条件；
 * 用户身份等可信参数必须从 {@link ToolContext} 读取。模型无法通过修改工具参数
 * 绕过登录用户的数据边界。</p>
 */
@Component
public class CatalogAiTools {
    private final AiCommerceGateway commerceGateway;
    private final CatalogRagService ragService;
    private final ObjectMapper objectMapper;

    public CatalogAiTools(
            AiCommerceGateway commerceGateway,
            CatalogRagService ragService,
            ObjectMapper objectMapper
    ) {
        this.commerceGateway = commerceGateway;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按商城或外卖类型查询在售商品。
     *
     * <p>返回值使用数据库中的实时价格、库存和销量，而不是向量库中的旧快照。</p>
     */
    @Tool(description = "Search mall products or instant-delivery dishes by business type and keyword")
    public String searchCatalog(
            @ToolParam(description = "MALL for products or DELIVERY for food") String bizType,
            @ToolParam(description = "Search keyword, may be empty") String keyword
    ) {
        // 关键词查询读取 MySQL 的实时价格、库存和销量。
        return json(commerceGateway.searchProducts(normalizeType(bizType), keyword));
    }

    /**
     * 结合向量召回、预算和实时商品数据进行推荐。
     *
     * <p>向量检索只负责发现语义相关的候选商品；最终名称、价格、库存和上下架状态
     * 仍以商品表查询结果为准，避免过期索引导致错误推荐。</p>
     */
    @Tool(description = "Recommend products using product catalog and semantic product knowledge")
    public String recommend(
            @ToolParam(description = "Shopping or food preference") String preference,
            @ToolParam(description = "Maximum budget in CNY") double budget,
            @ToolParam(description = "MALL or DELIVERY") String bizType
    ) {
        // 先做向量召回，再与 SQL 商品列表合并；最终价格和库存仍以 SQL 为准。
        List<Document> semantic = ragService.semanticSearch(preference + " " + bizType, 5);
        List<Long> vectorIds = semantic.stream()
                .map(document -> document.getMetadata().get("productId"))
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue())
                .toList();
        List<AiContracts.Product> candidates = commerceGateway.searchProducts(normalizeType(bizType), null);
        List<AiContracts.Product> ranked = candidates.stream()
                .filter(product -> product.price().compareTo(BigDecimal.valueOf(budget)) <= 0)
                .sorted(Comparator.comparingInt((AiContracts.Product product) -> vectorIds.contains(product.id()) ? 0 : 1)
                        .thenComparing(AiContracts.Product::price))
                .limit(5)
                .toList();
        return json(Map.of("recommendations", ranked, "semanticSources", semantic));
    }

    /**
     * 生成下单草稿，但不创建订单、不扣库存、不发起支付。
     *
     * <p>商城商品和闪购菜品必须分别结算，混选时直接拒绝，防止类型错误的订单
     * 进入支付链路。</p>
     */
    @Tool(description = "Build a checkout draft without creating an order or charging money")
    public String buildOrderDraft(
            @ToolParam(description = "Product IDs selected by the user") List<Long> productIds,
            @ToolParam(description = "Address for delivery") String address
    ) {
        // 草稿只计算商品和配送时间，不扣库存，也不创建正式订单。
        List<AiContracts.Product> products = commerceGateway.requireProducts(productIds);
        BigDecimal total = products.stream()
                .map(AiContracts.Product::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BizType type = products.stream().anyMatch(p -> BizType.DELIVERY.name().equals(p.bizType()))
                ? BizType.DELIVERY
                : BizType.MALL;
        boolean mixedTypes = products.stream()
                .map(AiContracts.Product::bizType)
                .distinct()
                .count() > 1;
        if (mixedTypes) {
            throw new AiBusinessException("商城商品和闪购菜品需要分别下单");
        }
        return json(Map.of(
                "draftOnly", true,
                "bizType", type.name(),
                "items", products,
                "estimatedTotal", total,
                "deliveryMinutes", type == BizType.DELIVERY
                        ? commerceGateway.estimateDeliveryMinutes(address)
                        : 0,
                "requiresUserConfirmation", true
        ));
    }

    /**
     * 查询当前登录用户自己的订单。
     *
     * <p>用户 ID 来自服务端工具上下文，模型的工具参数中不包含 userId，
     * 因此不能通过猜测订单号读取其他用户订单。</p>
     */
    @Tool(description = "Get current status of an order owned by the current user")
    public String orderStatus(
            @ToolParam(description = "Order ID") Long orderId,
            ToolContext toolContext
    ) {
        try {
            return json(commerceGateway.orderStatus(orderId, currentUserId(toolContext))
                    .map(value -> (Object) value)
                    .orElse(Map.of("found", false, "message", "订单不存在或无权查看")));
        } catch (AiBusinessException exception) {
            // 对模型返回稳定业务结果，避免越权查询被包装成 500 异常。
            return json(Map.of("found", false, "message", "订单不存在或无权查看"));
        }
    }

    /**
     * 查询外卖订单的实时配送状态。
     *
     * <p>配送节点存在配送服务的 delivery_task 中，因此该工具通过统一网关读取，
     * 不再依赖商城订单表的初始状态。</p>
     */
    @Tool(description = "Get the current user's live delivery status, courier and ETA")
    public String deliveryStatus(
            @ToolParam(description = "Delivery order ID") Long orderId,
            ToolContext toolContext
    ) {
        return json(commerceGateway.deliveryStatus(orderId, currentUserId(toolContext))
                .map(value -> (Object) value)
                .orElse(Map.of("found", false, "message", "配送任务不存在或无权查看")));
    }

    /**
     * 返回当前用户购物车摘要，供助手解释商品数量和结算金额。
     */
    @Tool(description = "Get the current user's cart summary")
    public String cartSummary(ToolContext toolContext) {
        return json(commerceGateway.cartSummary(currentUserId(toolContext)));
    }

    /**
     * 估算闪购配送时间；商城订单不使用该工具。
     */
    @Tool(description = "Estimate instant-delivery minutes for an address")
    public int estimateDeliveryTime(@ToolParam(description = "Delivery address") String address) {
        return commerceGateway.estimateDeliveryMinutes(address);
    }

    private String normalizeType(String value) {
        return BizType.DELIVERY.name().equalsIgnoreCase(value) ? "DELIVERY" : "MALL";
    }

    private Long currentUserId(ToolContext toolContext) {
        Object value = toolContext.getContext().get("userId");
        if (!(value instanceof Number number)) {
            throw new AiBusinessException("智能导购登录状态无效");
        }
        return number.longValue();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AiBusinessException("AI 工具结果序列化失败");
        }
    }
}
