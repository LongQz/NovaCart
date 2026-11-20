package com.novacart.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unified physical product and food SKU.
 *
 * @author qzh
 */
@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private String category;
    private String bizType;
    private BigDecimal price;
    private Integer stock;
    @Version
    private Integer version;
    private Integer sales;
    private String description;
    private String tags;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
