package com.novacart.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novacart.mall.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Persists one row per externally delivered payment callback ID. */
public interface PaymentCallbackMapper extends BaseMapper<PaymentCallbackLog> {
    @Select("select * from payment_callback_log where callback_id = #{callbackId} limit 1")
    PaymentCallbackLog findByCallbackId(@Param("callbackId") String callbackId);
}
