package com.novacart.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.mall.entity.OrderItem;
import com.novacart.mall.entity.Orders;
import com.novacart.mall.entity.Review;
import com.novacart.mall.mapper.OrderItemMapper;
import com.novacart.mall.mapper.OrderMapper;
import com.novacart.mall.mapper.ReviewMapper;
import com.novacart.api.DeliveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Verified purchase reviews with one review per order. */
@Service
public class ReviewService {
    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DeliveryClient deliveryClient;

    public ReviewService(
            ReviewMapper reviewMapper,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            DeliveryClient deliveryClient
    ) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.deliveryClient = deliveryClient;
    }

    @Transactional
    public Review create(Long userId, Long orderId, int rating, String content) {
        // 只允许真实订单持有人评价；商城需已支付，外卖需已到达。
        if (rating < 1 || rating > 5) {
            throw new BizException("评分必须在 1 到 5 之间");
        }
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        boolean deliveryArrived = false;
        if ("DELIVERY".equals(order.getBizType())) {
            var task = deliveryClient.getTask(orderId);
            deliveryArrived = task.data() != null && "ARRIVED".equals(task.data().status().name());
        }
        boolean mallPaid = "MALL".equals(order.getBizType()) && "PAID".equals(order.getStatus());
        if (!deliveryArrived && !mallPaid) {
            throw new BizException("订单完成后才能评价");
        }
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)) > 0) {
            throw new BizException("该订单已经评价");
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        Long productId = items.isEmpty() ? null : items.get(0).getProductId();
        Review review = new Review();
        review.setUserId(userId);
        review.setOrderId(orderId);
        review.setProductId(productId);
        review.setRating(rating);
        review.setContent(content == null ? "" : content.trim());
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        order.setStatus("REVIEWED");
        orderMapper.updateById(order);
        return review;
    }

    public List<Review> forProduct(Long productId) {
        // 商品页评价按最新提交时间倒序返回。
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .orderByDesc(Review::getId));
    }
}
