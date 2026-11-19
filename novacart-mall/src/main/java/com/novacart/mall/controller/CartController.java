package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.dto.CartSummary;
import com.novacart.mall.service.CartService;
import com.novacart.mall.dto.AuthUser;
import com.novacart.mall.service.AuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 商城购物车接口；外卖菜品采用直接购买，不进入购物车。 */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;
    private final AuthService authService;

    public CartController(CartService cartService, AuthService authService) {
        this.cartService = cartService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<CartSummary> get(@RequestHeader("Authorization") String authorization) {
        // 购物车归属于当前 Token 用户，不能通过请求参数伪造用户 ID。
        return ApiResponse.ok(cartService.getCart(authService.requireUser(authorization).id()));
    }

    @PostMapping("/items")
    public ApiResponse<CartSummary> add(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity
    ) {
        // 正数增加数量，负数减少数量，减到零时自动移除。
        AuthUser user = authService.requireUser(authorization);
        return ApiResponse.ok(cartService.add(user.id(), productId, quantity));
    }

    @DeleteMapping
    public ApiResponse<Void> clear(@RequestHeader("Authorization") String authorization) {
        // 清空当前用户购物车和已填写的联系人信息。
        cartService.clear(authService.requireUser(authorization).id());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/items/{productId}")
    public ApiResponse<CartSummary> remove(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId
    ) {
        // 删除单个购物车商品，不影响其他商品。
        Long userId = authService.requireUser(authorization).id();
        cartService.removeItem(userId, productId);
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @PostMapping("/contact")
    public ApiResponse<CartSummary> contact(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> request
    ) {
        // 收货人、电话与购物车商品分开存储，供结算页回显。
        Long userId = authService.requireUser(authorization).id();
        cartService.updateContact(userId, request.get("recipient"), request.get("phone"));
        return ApiResponse.ok(cartService.getCart(userId));
    }
}
