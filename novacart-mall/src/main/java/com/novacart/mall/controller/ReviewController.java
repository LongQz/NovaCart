package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.entity.Review;
import com.novacart.mall.service.AuthService;
import com.novacart.mall.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单评价接口。
 *
 * <p>写入评价必须登录且订单已支付或已送达；商品评价列表允许匿名读取。</p>
 */
@RestController
public class ReviewController {
    private final ReviewService reviewService;
    private final AuthService authService;

    public ReviewController(ReviewService reviewService, AuthService authService) {
        this.reviewService = reviewService;
        this.authService = authService;
    }

    @PostMapping("/api/reviews")
    public ApiResponse<Review> create(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request
    ) {
        // 评价人必须是订单持有人，评价后订单状态更新为 REVIEWED。
        Long userId = authService.requireUser(authorization).id();
        Long orderId = Long.valueOf(request.get("orderId").toString());
        int rating = Integer.parseInt(request.get("rating").toString());
        return ApiResponse.ok(reviewService.create(userId, orderId, rating, String.valueOf(request.get("content"))));
    }

    @GetMapping("/api/products/{productId}/reviews")
    public ApiResponse<List<Review>> forProduct(@PathVariable Long productId) {
        // 商品评价为公开只读数据。
        return ApiResponse.ok(reviewService.forProduct(productId));
    }
}
