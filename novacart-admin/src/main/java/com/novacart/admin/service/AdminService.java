package com.novacart.admin.service;

import com.novacart.api.DeliveryClient;
import com.novacart.common.DeliveryStatus;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 开发者端运营服务。
 *
 * <p>负责商品、订单、物流和客服运营。所有 SQL 都限定明确字段，
 * 不支持前端传入任意表名或列名。</p>
 */
@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;
    private final DeliveryClient deliveryClient;
    private final StringRedisTemplate redisTemplate;

    public AdminService(
            JdbcTemplate jdbcTemplate,
            DeliveryClient deliveryClient,
            StringRedisTemplate redisTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryClient = deliveryClient;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> dashboard() {
        // 总览只返回运营关心的计数，不暴露数据库表结构。
        return Map.of(
                "products", jdbcTemplate.queryForObject("select count(*) from product", Long.class),
                "activeProducts", jdbcTemplate.queryForObject(
                        "select count(*) from product where active=1", Long.class),
                "orders", jdbcTemplate.queryForObject("select count(*) from orders", Long.class),
                "pendingCancellations", jdbcTemplate.queryForObject(
                        "select count(*) from orders where status not in ('CANCELLED','ARRIVED','REVIEWED','COMPLETED')",
                        Long.class)
        );
    }

    public List<Map<String, Object>> products(String keyword) {
        String sql = """
                select id, shop_id, name, category, biz_type, price, stock, sales, active, description
                from product
                where (? = '' or name like concat('%', ?, '%'))
                order by id desc
                """;
        return jdbcTemplate.queryForList(sql, keyword == null ? "" : keyword, keyword == null ? "" : keyword);
    }

    public void updateProduct(Long id, Map<String, Object> request) {
        // 只允许修改价格、库存、上下架状态和描述，避免任意字段越权更新。
        Object price = request.get("price");
        Object stock = request.get("stock");
        Object active = request.get("active");
        Object description = request.get("description");
        jdbcTemplate.update("""
                        update product
                        set price = coalesce(?, price),
                            stock = coalesce(?, stock),
                            active = coalesce(?, active),
                            description = coalesce(?, description),
                            update_time = now()
                        where id = ?
                        """,
                price == null ? null : new BigDecimal(price.toString()),
                stock == null ? null : Integer.parseInt(stock.toString()),
                active == null ? null : Boolean.parseBoolean(active.toString()),
                description,
                id);
        if (stock != null) {
            redisTemplate.opsForValue().set("stock:product:" + id, stock.toString());
        }
    }

    public List<Map<String, Object>> orders() {
        return jdbcTemplate.queryForList("""
                select o.id, o.order_no, o.user_id, o.biz_type,
                       coalesce(d.status, o.status) as status,
                       o.total_amount, o.recipient, o.phone, o.address, o.cancel_reason, o.created_at
                from orders o left join delivery_task d on d.order_id = o.id
                order by o.id desc limit 100
                """);
    }

    @GlobalTransactional(name = "novacart-customer-cancel-order", rollbackFor = Exception.class)
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        // 客服取消属于跨服务写操作：先关闭配送，再更新订单和回补商品库存。
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "select id, biz_type, status from orders where id = ?", orderId);
        String bizType = String.valueOf(order.get("biz_type"));
        if ("DELIVERY".equals(bizType)) {
            var task = deliveryClient.getTask(orderId);
            if (task.data() != null && List.of(
                    DeliveryStatus.SHIPPED,
                    DeliveryStatus.DELIVERING,
                    DeliveryStatus.ARRIVED
            ).contains(task.data().status())) {
                throw new IllegalStateException("商家已发货，客服不能取消订单");
            }
            deliveryClient.cancel(orderId);
        }
        jdbcTemplate.update(
                "update orders set status='CANCELLED', cancel_reason=? where id=?",
                reason == null ? "客服取消" : reason,
                orderId);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "select product_id, quantity from order_item where order_id=?", orderId);
        items.forEach(item -> {
            jdbcTemplate.update(
                    "update product set stock=stock+? where id=?",
                    item.get("quantity"),
                    item.get("product_id"));
            String key = "stock:product:" + item.get("product_id");
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.opsForValue().increment(key, Long.parseLong(item.get("quantity").toString()));
            }
        });
    }

    public void advanceDelivery(Long orderId) {
        // 开发者控制台手动推进物流，便于演示商家接单和配送阶段。
        deliveryClient.advance(orderId);
    }

    public List<Map<String, Object>> supportThreads() {
        return jdbcTemplate.queryForList("""
                select u.id as user_id, u.username, u.nickname,
                       (select content from support_message m where m.user_id=u.id order by id desc limit 1) as last_message,
                       (select created_at from support_message m where m.user_id=u.id order by id desc limit 1) as last_time
                from user_account u
                where exists (select 1 from support_message m where m.user_id=u.id)
                order by last_time desc
                """);
    }

    public List<Map<String, Object>> supportMessages(Long userId) {
        return jdbcTemplate.queryForList(
                "select id,user_id,order_id,sender_role,content,created_at from support_message "
                        + "where user_id=? order by id asc",
                userId);
    }

    public void reply(Long userId, Long orderId, String content) {
        // 系统客服回复写入同一消息表，用户端刷新后即可看到 ADMIN 消息。
        jdbcTemplate.update(
                "insert into support_message(user_id,order_id,sender_role,content,created_at) values (?,?,?,?,?)",
                userId,
                orderId,
                "ADMIN",
                content,
                LocalDateTime.now());
    }
}
