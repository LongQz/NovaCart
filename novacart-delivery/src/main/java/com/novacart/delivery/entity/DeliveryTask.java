package com.novacart.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Delivery dispatch record for a mall order. */
@Data
@TableName("delivery_task")
public class DeliveryTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private String recipient;
    private String phone;
    private String address;
    private String courierName;
    private String status;
    private Integer etaMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
