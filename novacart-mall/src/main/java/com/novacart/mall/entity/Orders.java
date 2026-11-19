package com.novacart.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Unified mall or instant-delivery order. */
@Data
@TableName("orders")
public class Orders {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String requestKey;
    private Long userId;
    private String bizType;
    private String status;
    private BigDecimal totalAmount;
    private String recipient;
    private String phone;
    private String address;
    private String note;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
