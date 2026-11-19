package com.novacart.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.mall.dto.AuthUser;
import com.novacart.mall.dto.LoginRequest;
import com.novacart.mall.dto.LoginResponse;
import com.novacart.mall.dto.RegisterRequest;
import com.novacart.mall.entity.UserAccount;
import com.novacart.mall.mapper.UserAccountMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * 登录会话与角色校验服务。
 *
 * <p>用户认证信息保存在 Redis，数据库只保存账号和密码摘要。Token 每次请求
 * 都重新读取，因此退出、过期和角色变化不依赖本地 JVM 缓存。</p>
 */
@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserAccountMapper userAccountMapper, StringRedisTemplate redisTemplate) {
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
    }

    public LoginResponse login(LoginRequest request) {
        // 核对 SHA-256 密码摘要，并生成只存在于 Redis 的随机会话 Token。
        if (request == null || request.username() == null || request.password() == null) {
            throw new UnauthorizedException("请输入用户名和密码");
        }
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username().trim())
                .eq(UserAccount::getActive, true)
                .last("limit 1"));
        if (user == null || !sha256(request.password()).equalsIgnoreCase(user.getPasswordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        return issueToken(user);
    }

    public Map<String, Object> sendRegistrationCode(String phone) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new BizException("请输入正确的11位手机号");
        }
        boolean exists = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, phone)) > 0;
        if (exists) {
            throw new BizException("该手机号已经注册");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(registerCodeKey(phone), code, Duration.ofMinutes(5));
        // 本地作品集不接短信供应商，把验证码返回给开发环境页面直接展示。
        return Map.of("sent", true, "debugCode", code);
    }

    public LoginResponse register(RegisterRequest request) {
        if (request == null || request.username() == null || request.password() == null
                || request.nickname() == null || request.phone() == null) {
            throw new BizException("请完整填写注册信息");
        }
        String username = request.username().trim();
        String nickname = request.nickname().trim();
        String phone = request.phone().trim();
        if (!username.matches("^[A-Za-z0-9_]{4,24}$")) {
            throw new BizException("用户名需为4-24位字母、数字或下划线");
        }
        if (request.password().length() < 6) {
            throw new BizException("密码至少需要6位");
        }
        if (nickname.length() < 2 || nickname.length() > 20) {
            throw new BizException("昵称长度需为2-20位");
        }
        String cachedCode = redisTemplate.opsForValue().get(registerCodeKey(phone));
        if (cachedCode == null || !cachedCode.equals(request.code())) {
            throw new BizException("验证码错误或已过期");
        }
        if (userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)) > 0) {
            throw new BizException("用户名已被使用");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(sha256(request.password()));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setRole("USER");
        user.setActive(true);
        userAccountMapper.insert(user);
        redisTemplate.delete(registerCodeKey(phone));
        return issueToken(user);
    }

    private LoginResponse issueToken(UserAccount user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForHash().putAll(tokenKey(token), Map.of(
                "userId", user.getId().toString(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));
        redisTemplate.expire(tokenKey(token), Duration.ofDays(7));
        return new LoginResponse(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), token);
    }

    public AuthUser requireUser(String authorization) {
        // 同时兼容 Bearer 前缀和裸 Token；每次受保护请求都重新校验。
        String token = normalize(authorization);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(tokenKey(token));
        if (data == null || data.isEmpty()) {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
        return new AuthUser(
                Long.valueOf(data.get("userId").toString()),
                data.get("username").toString(),
                data.get("nickname").toString(),
                data.get("role").toString()
        );
    }

    public AuthUser requireAdmin(String authorization) {
        // 开发者端复用同一 Token，但必须额外满足 ADMIN 角色。
        AuthUser user = requireUser(authorization);
        if (!"ADMIN".equals(user.role())) {
            throw new UnauthorizedException("需要开发者权限");
        }
        return user;
    }

    private String tokenKey(String token) {
        return "novacart:auth:" + token;
    }

    private String registerCodeKey(String phone) {
        return "novacart:register:code:" + phone;
    }

    private String normalize(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("请先登录");
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
