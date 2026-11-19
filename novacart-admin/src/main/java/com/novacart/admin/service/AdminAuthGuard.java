package com.novacart.admin.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 开发者端权限守卫。
 *
 * <p>复用商城登录 Token，但每个管理接口都重新校验 ADMIN 角色，
 * 不依赖前端是否隐藏按钮。</p>
 */
@Component
public class AdminAuthGuard {
    private final StringRedisTemplate redisTemplate;

    public AdminAuthGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<Object, Object> requireAdmin(String authorization) {
        // 读取商城登录时写入 Redis 的 Token，并要求 role=ADMIN。
        String token = authorization == null ? "" : authorization.replaceFirst("^Bearer ", "");
        Map<Object, Object> data = redisTemplate.opsForHash().entries("novacart:auth:" + token);
        if (data == null || data.isEmpty() || !"ADMIN".equals(String.valueOf(data.get("role")))) {
            throw new SecurityException("请使用开发者账号登录");
        }
        return data;
    }
}
