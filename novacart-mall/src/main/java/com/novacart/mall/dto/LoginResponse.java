package com.novacart.mall.dto;

/** Issued login token and basic account data. */
public record LoginResponse(Long userId, String username, String nickname, String role, String token) {
}
