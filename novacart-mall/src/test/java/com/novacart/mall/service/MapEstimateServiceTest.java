package com.novacart.mall.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapEstimateServiceTest {
    @Test
    void fallbackEstimateIsStableAndBounded() {
        MapEstimateService service = new MapEstimateService();

        int first = service.estimateMinutes("重庆邮电大学 24 栋");
        int second = service.estimateMinutes("重庆邮电大学 24 栋");

        assertThat(first).isEqualTo(second).isBetween(18, 45);
    }
}
