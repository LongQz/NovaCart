package com.novacart.mall.service;

import com.novacart.mall.entity.Product;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Redis inventory gate and atomic Lua pre-deduction for checkout traffic. */
@Service
public class RedisStockService {
    private static final String STOCK_KEY = "stock:product:";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT;
    private static final DefaultRedisScript<Long> RESTORE_SCRIPT;

    static {
        RESERVE_SCRIPT = new DefaultRedisScript<>();
        RESERVE_SCRIPT.setLocation(new ClassPathResource("redis_stock_reserve.lua"));
        RESERVE_SCRIPT.setResultType(Long.class);
        RESTORE_SCRIPT = new DefaultRedisScript<>();
        RESTORE_SCRIPT.setLocation(new ClassPathResource("redis_stock_restore.lua"));
        RESTORE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void load(Product product) {
        redisTemplate.opsForValue().setIfAbsent(STOCK_KEY + product.getId(), String.valueOf(product.getStock()));
    }

    public void sync(Long productId, int stock) {
        redisTemplate.opsForValue().set(STOCK_KEY + productId, String.valueOf(stock));
    }

    public void reserve(Map<Long, Integer> quantities) {
        List<String> keys = new ArrayList<>(quantities.size());
        Object[] args = new Object[quantities.size()];
        int index = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            keys.add(STOCK_KEY + entry.getKey());
            args[index++] = String.valueOf(entry.getValue());
        }
        Long blockedIndex = redisTemplate.execute(RESERVE_SCRIPT, keys, args);
        if (blockedIndex != null && blockedIndex > 0) {
            throw new BizException("商品 " + keys.get(blockedIndex.intValue() - 1) + " 库存不足");
        }
    }

    public void restore(Map<Long, Integer> quantities) {
        if (quantities.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(quantities.size());
        Object[] args = new Object[quantities.size()];
        int index = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            keys.add(STOCK_KEY + entry.getKey());
            args[index++] = String.valueOf(entry.getValue());
        }
        redisTemplate.execute(RESTORE_SCRIPT, keys, args);
    }
}
