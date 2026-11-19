package com.novacart.mall.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novacart.common.CartItem;
import com.novacart.mall.dto.CartLine;
import com.novacart.mall.dto.CartSummary;
import com.novacart.mall.entity.Product;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户购物车服务。
 *
 * <p>购物车只缓存商品 ID 和数量，商品名称、价格和库存每次读取时从商品服务
 * 重新加载，避免缓存中的旧价格进入结算流程。</p>
 */
@Service
public class CartService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductService productService;

    public CartService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, ProductService productService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.productService = productService;
    }

    public CartSummary getCart(Long userId) {
        // Redis 只保存商品 ID 和数量；名称、价格和库存始终从商品服务重新读取。
        List<CartItem> items = readItems(userId);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product product = productService.requireProduct(item.productId());
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity()));
            lines.add(new CartLine(
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    product.getPrice(),
                    item.quantity(),
                    subtotal
            ));
            total = total.add(subtotal);
        }
        Map<Object, Object> contact = redisTemplate.opsForHash().entries(contactKey(userId));
        return new CartSummary(
                lines,
                total,
                contact.getOrDefault("recipient", "").toString(),
                contact.getOrDefault("phone", "").toString()
        );
    }

    public CartSummary add(Long userId, Long productId, int quantity) {
        // quantity 可为负数，用于购物车减少数量；减到 0 时删除该商品。
        if (quantity == 0) {
            return getCart(userId);
        }
        Product product = productService.requireProduct(productId);
        if (quantity > product.getStock()) {
            throw new BizException("库存不足");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        readItems(userId).forEach(item -> quantities.put(item.productId(), item.quantity()));
        int current = quantities.getOrDefault(productId, 0);
        int next = Math.max(0, current + quantity);
        if (next == 0) {
            quantities.remove(productId);
        } else {
            quantities.put(productId, next);
        }
        writeItems(userId, quantities);
        return getCart(userId);
    }

    public void clear(Long userId) {
        redisTemplate.delete(key(userId));
        redisTemplate.delete(contactKey(userId));
    }

    public List<CartItem> getRawItems(Long userId) {
        return readItems(userId);
    }

    public void removeItem(Long userId, Long productId) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        readItems(userId).forEach(item -> quantities.put(item.productId(), item.quantity()));
        quantities.remove(productId);
        writeItems(userId, quantities);
    }

    public void updateContact(Long userId, String recipient, String phone) {
        // 收货人和电话单独存放，不因商品列表更新而丢失。
        redisTemplate.opsForHash().putAll(contactKey(userId), Map.of(
                "recipient", recipient == null ? "" : recipient.trim(),
                "phone", phone == null ? "" : phone.trim()
        ));
    }

    private String key(Long userId) {
        return "novacart:cart:" + userId;
    }

    private String contactKey(Long userId) {
        return "novacart:cart:contact:" + userId;
    }

    private List<CartItem> readItems(Long userId) {
        // 反序列化失败通常表示缓存结构已不兼容，提示用户清空而不是静默生成错误购物车。
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BizException("购物车数据损坏，请清空后重试");
        }
    }

    private void writeItems(Long userId, Map<Long, Integer> quantities) {
        List<CartItem> items = quantities.entrySet().stream()
                .map(entry -> new CartItem(entry.getKey(), entry.getValue()))
                .toList();
        try {
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(items));
        } catch (Exception exception) {
            throw new BizException("购物车保存失败");
        }
    }
}
