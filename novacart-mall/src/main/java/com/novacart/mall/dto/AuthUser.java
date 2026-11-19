package com.novacart.mall.dto;

/** Authenticated user extracted from the Redis token. */
public record AuthUser(Long id, String username, String nickname, String role) {
}
