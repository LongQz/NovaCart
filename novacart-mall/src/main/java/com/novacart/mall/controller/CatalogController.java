package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.entity.Product;
import com.novacart.mall.entity.Shop;
import com.novacart.mall.mapper.ShopMapper;
import com.novacart.mall.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public catalog APIs for mall and instant-delivery products. */
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final ProductService productService;
    private final ShopMapper shopMapper;

    public CatalogController(ProductService productService, ShopMapper shopMapper) {
        this.productService = productService;
        this.shopMapper = shopMapper;
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products(
            @RequestParam(defaultValue = "MALL") String type,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(productService.search(type.toUpperCase(), keyword));
    }

    @GetMapping("/shops")
    public ApiResponse<List<Shop>> shops(@RequestParam(required = false) String type) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop>()
                .eq(type != null && !type.isBlank(), Shop::getBizType, type);
        return ApiResponse.ok(shopMapper.selectList(query));
    }

    @GetMapping("/hot")
    public ApiResponse<List<Product>> hot(@RequestParam(defaultValue = "MALL") String type) {
        return ApiResponse.ok(productService.hotProducts(type.toUpperCase()));
    }
}
