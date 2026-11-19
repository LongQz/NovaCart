package com.novacart.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Merchant or restaurant storefront. */
@Data
@TableName("shop")
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String bizType;
    private String address;
    private Double rating;
    private Integer deliveryMinutes;
}
