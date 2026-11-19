package com.novacart.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novacart.common.DeliveryStatus;
import com.novacart.common.DeliveryTaskRequest;
import com.novacart.common.DeliveryTaskView;
import com.novacart.delivery.entity.DeliveryTask;
import com.novacart.delivery.mapper.DeliveryTaskMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Idempotent delivery task lifecycle and live status fan-out. */
@Service
public class DeliveryService {
    private final DeliveryTaskMapper taskMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public DeliveryService(
            DeliveryTaskMapper taskMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.taskMapper = taskMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public DeliveryTaskView createTask(DeliveryTaskRequest request) {
        // 以 orderId 为幂等键：OpenFeign 和 RabbitMQ 同时到达时只保留一条任务。
        DeliveryTask existing = findEntity(request.orderId());
        if (existing != null) {
            return view(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        DeliveryTask task = new DeliveryTask();
        task.setOrderId(request.orderId());
        task.setUserId(request.userId());
        task.setRecipient(request.recipient());
        task.setPhone(request.phone());
        task.setAddress(request.address());
        task.setStatus(DeliveryStatus.PENDING_MERCHANT.name());
        task.setEtaMinutes(25 + Math.abs(request.address().hashCode()) % 20);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        publish(task);
        return view(task);
    }

    @Transactional
    public DeliveryTaskView dispatch(Long orderId) {
        // 商家接单：待商家接单 → 待商家发货。
        DeliveryTask task = requireTask(orderId);
        if (DeliveryStatus.PENDING_MERCHANT.name().equals(task.getStatus())) {
            task.setStatus(DeliveryStatus.PENDING_SHIPMENT.name());
            task.setCourierName("NovaRider-" + (100 + task.getId() % 900));
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            publish(task);
        }
        return view(task);
    }

    @Transactional
    public DeliveryTaskView advance(Long orderId) {
        // 依次推进：待发货 → 已发货 → 送货中 → 已到达。
        DeliveryTask task = requireTask(orderId);
        DeliveryStatus current = DeliveryStatus.valueOf(task.getStatus());
        DeliveryStatus next = switch (current) {
            case PENDING_MERCHANT -> DeliveryStatus.PENDING_SHIPMENT;
            case PENDING_SHIPMENT -> DeliveryStatus.SHIPPED;
            case SHIPPED -> DeliveryStatus.DELIVERING;
            case DELIVERING -> DeliveryStatus.ARRIVED;
            case ARRIVED -> DeliveryStatus.ARRIVED;
            case CANCELLED -> DeliveryStatus.CANCELLED;
        };
        task.setStatus(next.name());
        if (task.getCourierName() == null) {
            task.setCourierName("NovaRider-" + (100 + task.getId() % 900));
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        publish(task);
        return view(task);
    }

    @Transactional
    public DeliveryTaskView cancel(Long orderId) {
        // 一旦进入 SHIPPED，配送服务拒绝取消，和用户端规则保持一致。
        DeliveryTask task = requireTask(orderId);
        if (currentAtOrAfterShipped(task)) {
            throw new IllegalStateException("商家已发货，订单不能取消");
        }
        task.setStatus(DeliveryStatus.CANCELLED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        publish(task);
        return view(task);
    }

    public DeliveryTaskView get(Long orderId) {
        return view(requireTask(orderId));
    }

    public List<DeliveryTaskView> list(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<DeliveryTask>()
                        .eq(DeliveryTask::getUserId, userId)
                        .orderByDesc(DeliveryTask::getId))
                .stream()
                .map(this::view)
                .toList();
    }

    private DeliveryTask findEntity(Long orderId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<DeliveryTask>()
                .eq(DeliveryTask::getOrderId, orderId)
                .last("limit 1"));
    }

    private DeliveryTask requireTask(Long orderId) {
        DeliveryTask task = findEntity(orderId);
        if (task == null) {
            throw new IllegalArgumentException("配送任务不存在");
        }
        return task;
    }

    private void publish(DeliveryTask task) {
        // 前端可订阅 /topic/delivery/{orderId} 接收配送状态变化。
        messagingTemplate.convertAndSend("/topic/delivery/" + task.getOrderId(), view(task));
    }

    private DeliveryTaskView view(DeliveryTask task) {
        return new DeliveryTaskView(
                task.getId(),
                task.getOrderId(),
                task.getUserId(),
                task.getRecipient(),
                task.getPhone(),
                task.getAddress(),
                task.getCourierName(),
                DeliveryStatus.valueOf(task.getStatus()),
                task.getEtaMinutes(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private boolean currentAtOrAfterShipped(DeliveryTask task) {
        return List.of(
                DeliveryStatus.SHIPPED.name(),
                DeliveryStatus.DELIVERING.name(),
                DeliveryStatus.ARRIVED.name()
        ).contains(task.getStatus());
    }
}
