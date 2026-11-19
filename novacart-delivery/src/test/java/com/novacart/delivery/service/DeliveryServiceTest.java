package com.novacart.delivery.service;

import com.novacart.common.DeliveryStatus;
import com.novacart.delivery.entity.DeliveryTask;
import com.novacart.delivery.mapper.DeliveryTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryServiceTest {
    @Test
    void advanceMovesWaitingTaskToAssigned() {
        DeliveryTaskMapper mapper = Mockito.mock(DeliveryTaskMapper.class);
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        DeliveryTask task = new DeliveryTask();
        task.setId(1L);
        task.setOrderId(9L);
        task.setUserId(1L);
        task.setAddress("重庆邮电大学");
        task.setStatus(DeliveryStatus.PENDING_MERCHANT.name());
        task.setEtaMinutes(25);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        Mockito.when(mapper.selectOne(ArgumentMatchers.any())).thenReturn(task);

        DeliveryService service = new DeliveryService(mapper, messagingTemplate);
        var result = service.advance(9L);

        assertThat(result.status()).isEqualTo(DeliveryStatus.PENDING_SHIPMENT);
        assertThat(result.courierName()).startsWith("NovaRider-");
        Mockito.verify(mapper).updateById(task);
    }
}
