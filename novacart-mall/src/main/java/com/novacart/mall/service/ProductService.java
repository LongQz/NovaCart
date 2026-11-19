package com.novacart.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.mall.entity.Product;
import com.novacart.mall.mapper.ProductMapper;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品目录查询服务。
 *
 * <p>普通查询使用 Spring Cache 降低热点分类的数据库压力；商品售价、库存和
 * 上下架状态在下单时仍会重新校验，不直接信任缓存结果。</p>
 */
@Service
public class ProductService {
    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Cacheable(value = "catalog", key = "#type + ':' + (#keyword == null ? '' : #keyword)")
    @SentinelResource(value = "catalogSearch", fallback = "catalogSearchFallback")
    public List<Product> search(String type, String keyword) {
        // 不返回已下架商品；关键词同时匹配名称、描述和标签。
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getActive, true)
                .eq(type != null && !type.isBlank(), Product::getBizType, type)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(Product::getName, keyword)
                        .or()
                        .like(Product::getDescription, keyword)
                        .or()
                        .like(Product::getTags, keyword))
                .orderByDesc(Product::getId);
        return productMapper.selectList(query);
    }

    public List<Product> catalogSearchFallback(String type, String keyword, Throwable throwable) {
        // 搜索链路异常时返回空列表，由页面展示暂无结果并允许用户重试。
        return List.of();
    }

    public Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Boolean.TRUE.equals(product.getActive())) {
            throw new BizException("商品不存在或已下架");
        }
        return product;
    }

    @Cacheable(value = "hotCatalog", key = "#type")
    public List<Product> hotProducts(String type) {
        // 热点榜按销量倒序，商城和外卖分别查询。
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getActive, true)
                .eq(type != null && !type.isBlank(), Product::getBizType, type)
                .orderByDesc(Product::getSales)
                .last("limit 6"));
    }
}
