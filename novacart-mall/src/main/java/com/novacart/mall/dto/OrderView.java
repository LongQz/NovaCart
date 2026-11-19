package com.novacart.mall.dto;

import com.novacart.mall.entity.OrderItem;
import com.novacart.mall.entity.Orders;

import java.util.List;

/** Order with line items and optional delivery state. */
public record OrderView(Orders order, List<OrderItem> items, Object delivery) {
}
