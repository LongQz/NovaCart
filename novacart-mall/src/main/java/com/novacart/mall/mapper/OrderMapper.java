package com.novacart.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novacart.mall.entity.Orders;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Order persistence and lifecycle queries. */
public interface OrderMapper extends BaseMapper<Orders> {
    @Select("select * from orders where request_key = #{requestKey} limit 1")
    Orders findByRequestKey(@Param("requestKey") String requestKey);

    @Select("select * from orders where status = 'PENDING_PAYMENT' and created_at < date_sub(now(), interval 30 minute)")
    List<Orders> findExpiredOrders();
}
