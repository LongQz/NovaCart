package com.novacart.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Unique payment callback record used for webhook idempotency. */
@Data
@TableName("payment_callback_log")
public class PaymentCallbackLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String callbackId;
    private Long orderId;
    private String payload;
    private LocalDateTime processedAt;
}
