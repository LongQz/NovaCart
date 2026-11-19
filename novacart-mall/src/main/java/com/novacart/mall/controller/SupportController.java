package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.entity.SupportMessage;
import com.novacart.mall.service.AuthService;
import com.novacart.mall.service.SupportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 用户客服消息接口；所有操作都从登录 Token 确定用户身份。 */
@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService supportService;
    private final AuthService authService;

    public SupportController(SupportService supportService, AuthService authService) {
        this.supportService = supportService;
        this.authService = authService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<SupportMessage>> history(@RequestHeader("Authorization") String authorization) {
        // 只返回当前登录用户的客服会话。
        return ApiResponse.ok(supportService.history(authService.requireUser(authorization).id()));
    }

    @PostMapping("/messages")
    public ApiResponse<SupportMessage> send(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request
    ) {
        // 用户消息发送后，开发者端会在客服消息列表中看到该会话；订单号可选，
        // 但传入时服务层会校验归属。
        Long userId = authService.requireUser(authorization).id();
        Long orderId = request.get("orderId") == null ? null : Long.valueOf(request.get("orderId").toString());
        return ApiResponse.ok(supportService.send(userId, orderId, String.valueOf(request.get("content"))));
    }
}
