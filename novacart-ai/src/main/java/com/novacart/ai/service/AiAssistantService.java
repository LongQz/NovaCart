package com.novacart.ai.service;

import com.novacart.ai.tool.CatalogAiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 商城与闪购共用的 Spring AI 导购助手。
 *
 * <p>该类位于独立的 novacart-ai 模块。模型只负责理解需求和组织回答，
 * 商品价格、库存、订单归属、配送时间等事实必须来自工具结果。</p>
 */
@Service
public class AiAssistantService {
    private final ChatClient chatClient;

    public AiAssistantService(ChatClient.Builder builder, CatalogAiTools tools) {
        // 系统提示词约束回答边界；ChatClient 负责完成“模型选择工具 -> 执行工具
        // -> 把结果回传模型 -> 生成最终回答”的完整调用链。
        this.chatClient = builder
                .defaultSystem("""
                        你是 NovaCart 智能购物与闪购助手。
                        回答前优先调用商品搜索、推荐、订单状态、配送状态或 RAG 工具。
                        只能基于工具返回的库存、价格和商品信息作答，不得编造。
                        商品口味、规格只能使用商品描述和标签中明确存在的信息。
                        商城商品与闪购菜品必须分别下单。
                        创建正式订单前必须展示商品、数量、金额和配送地址，并要求用户确认。
                        用户未提供地址时不得编造默认地址，也不要给出具体配送时间；地址明确后再调用 estimateDeliveryTime。
                        用户询问外卖送到哪里时，应调用 deliveryStatus 获取实时配送节点，不能只根据订单初始状态推断。
                        回答使用中文，结构为：推荐结果、选择理由、下单前确认事项。
                        """)
                .defaultTools(tools)
                .build();
    }

    /**
     * 处理一轮导购对话。
     *
     * @param message 用户当前输入
     * @param userId  已登录用户 ID，由控制器从 Token 中解析
     * @return 基于实时商品与订单数据生成的中文回答
     */
    public String chat(String message, Long userId) {
        if (message == null || message.isBlank()) {
            throw new AiBusinessException("请输入需要咨询的商品或口味");
        }
        if (userId == null) {
            throw new AiBusinessException("请先登录后使用智能导购");
        }
        // userId 只放进工具上下文，不会拼进提示词，也不能由模型修改。
        return chatClient.prompt()
                .user(message)
                .toolContext(Map.of("userId", userId))
                .call()
                .content();
    }
}
