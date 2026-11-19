package com.novacart.admin.controller;

import com.novacart.admin.service.AdminAuthGuard;
import com.novacart.admin.service.AdminService;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Developer console APIs with role validation on every request. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAuthGuard authGuard;
    private final AdminService adminService;

    public AdminController(AdminAuthGuard authGuard, AdminService adminService) {
        this.authGuard = authGuard;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader("Authorization") String token) {
        // 每个开发者接口都显式校验 ADMIN 角色。
        authGuard.requireAdmin(token);
        return adminService.dashboard();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "") String keyword
    ) {
        authGuard.requireAdmin(token);
        return adminService.products(keyword);
    }

    @PutMapping("/products/{id}")
    public Map<String, Object> updateProduct(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> request
    ) {
        // 支持库存、价格、描述和上下架状态修改。
        authGuard.requireAdmin(token);
        adminService.updateProduct(id, request);
        return Map.of("updated", true);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestHeader("Authorization") String token) {
        authGuard.requireAdmin(token);
        return adminService.orders();
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request
    ) {
        // 客服取消前由 AdminService 检查外卖是否已经发货。
        authGuard.requireAdmin(token);
        adminService.cancelOrder(id, request == null ? "客服取消" : request.get("reason"));
        return Map.of("cancelled", true);
    }

    @PostMapping("/delivery/{orderId}/advance")
    public Map<String, Object> advanceDelivery(
            @RequestHeader("Authorization") String token,
            @PathVariable Long orderId
    ) {
        authGuard.requireAdmin(token);
        adminService.advanceDelivery(orderId);
        return Map.of("advanced", true);
    }

    @GetMapping("/support/threads")
    public List<Map<String, Object>> threads(@RequestHeader("Authorization") String token) {
        authGuard.requireAdmin(token);
        return adminService.supportThreads();
    }

    @GetMapping("/support/{userId}")
    public List<Map<String, Object>> messages(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId
    ) {
        authGuard.requireAdmin(token);
        return adminService.supportMessages(userId);
    }

    @PostMapping("/support/{userId}/reply")
    public Map<String, Object> reply(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request
    ) {
        // 回复写入共享消息表，用户端消息中心可立即看到。
        authGuard.requireAdmin(token);
        Long orderId = request.get("orderId") == null ? null : Long.valueOf(request.get("orderId").toString());
        adminService.reply(userId, orderId, String.valueOf(request.get("content")));
        return Map.of("sent", true);
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public Map<String, Object> unauthorized(SecurityException exception) {
        return Map.of("success", false, "message", exception.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public Map<String, Object> missingAuthorization(MissingRequestHeaderException exception) {
        return Map.of("success", false, "message", "请使用开发者账号登录");
    }
}
