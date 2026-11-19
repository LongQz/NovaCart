package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.dto.AuthUser;
import com.novacart.mall.dto.LoginRequest;
import com.novacart.mall.dto.LoginResponse;
import com.novacart.mall.dto.RegisterRequest;
import com.novacart.mall.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User and developer login entry points. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        // 登录成功后将用户、角色和 Token 写入 Redis。
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/code")
    public ApiResponse<java.util.Map<String, Object>> sendRegisterCode(@RequestParam String phone) {
        return ApiResponse.ok(authService.sendRegistrationCode(phone));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUser> me(@RequestHeader("Authorization") String authorization) {
        // 个人中心使用该接口验证当前 Token。
        return ApiResponse.ok(authService.requireUser(authorization));
    }
}
