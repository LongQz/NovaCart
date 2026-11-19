package com.novacart.mall.dto;

/** User registration payload with a local verification code. */
public record RegisterRequest(
        String username,
        String password,
        String nickname,
        String phone,
        String code
) {
}
