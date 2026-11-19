package com.novacart.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.mall.entity.SupportMessage;
import com.novacart.mall.mapper.SupportMessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户侧客服会话服务。
 *
 * <p>用户和开发者共用一张消息表，通过 senderRole 区分消息方向。读取时始终按
 * 用户 ID 过滤，避免串会话。</p>
 */
@Service
public class SupportService {
    private final SupportMessageMapper messageMapper;
    private final OrderService orderService;

    public SupportService(SupportMessageMapper messageMapper, OrderService orderService) {
        this.messageMapper = messageMapper;
        this.orderService = orderService;
    }

    public List<SupportMessage> history(Long userId) {
        // 每个用户只能读取自己的客服会话，查询条件包含 userId。
        return messageMapper.selectList(new LambdaQueryWrapper<SupportMessage>()
                .eq(SupportMessage::getUserId, userId)
                .orderByAsc(SupportMessage::getId));
    }

    public SupportMessage send(Long userId, Long orderId, String content) {
        // 用户消息标记为 USER；关联订单时必须确认该订单属于当前用户。
        if (content == null || content.isBlank()) {
            throw new BizException("消息不能为空");
        }
        if (orderId != null) {
            orderService.getOwned(orderId, userId);
        }
        SupportMessage message = new SupportMessage();
        message.setUserId(userId);
        message.setOrderId(orderId);
        message.setSenderRole("USER");
        message.setContent(content.trim());
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }
}
