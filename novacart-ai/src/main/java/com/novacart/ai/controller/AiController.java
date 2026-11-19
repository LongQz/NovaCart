package com.novacart.ai.controller;

import com.novacart.ai.gateway.AiCommerceGateway;
import com.novacart.ai.gateway.AiContracts;
import com.novacart.ai.rag.CatalogRagService;
import com.novacart.ai.service.AiAssistantService;
import com.novacart.ai.service.AiBusinessException;
import com.novacart.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能导购与商品知识索引接口。
 *
 * <p>普通对话要求有效登录态；索引重建会调用外部 Embedding 服务，
 * 只允许开发者角色触发。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiAssistantService assistantService;
    private final CatalogRagService ragService;
    private final AiCommerceGateway commerceGateway;

    public AiController(
            AiAssistantService assistantService,
            CatalogRagService ragService,
            AiCommerceGateway commerceGateway
    ) {
        this.assistantService = assistantService;
        this.ragService = ragService;
        this.commerceGateway = commerceGateway;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> request
    ) {
        // 从服务端会话取得 userId，再通过 ToolContext 传给业务工具。
        AiContracts.User user = commerceGateway.requireUser(authorization);
        String message = request.getOrDefault("message", "");
        return ApiResponse.ok(Map.of("answer", assistantService.chat(message, user.id())));
    }

    @PostMapping("/reindex")
    public ApiResponse<Map<String, Integer>> reindex(
            @RequestHeader("Authorization") String authorization
    ) {
        // 索引重建成本较高，不能开放给匿名请求。
        AiContracts.User user = commerceGateway.requireUser(authorization);
        if (!"ADMIN".equals(user.role())) {
            throw new AiBusinessException(HttpStatus.UNAUTHORIZED, "需要开发者权限");
        }
        return ApiResponse.ok(Map.of("indexed", ragService.reindex()));
    }
}
