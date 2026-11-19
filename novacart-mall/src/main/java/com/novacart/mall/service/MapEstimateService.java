package com.novacart.mall.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 配送时长估算服务。
 *
 * <p>配置地图接口时使用 HttpClient 请求真实结果；未配置或接口异常时退化为
 * 稳定的本地估算，保证演示环境不依赖第三方地图 Key。</p>
 */
@Service
public class MapEstimateService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Value("${novacart.map.estimate-url:}")
    private String estimateUrl;

    public int estimateMinutes(String address) {
        if (estimateUrl != null && !estimateUrl.isBlank()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(estimateUrl))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String digits = response.body().replaceAll("[^0-9]", "");
                    if (!digits.isBlank()) {
                        return Math.max(5, Math.min(90, Integer.parseInt(digits)));
                    }
                }
            } catch (Exception ignored) {
                // 地图服务不可用时继续走本地估算，不能让可选依赖阻断下单。
            }
        }
        int stable = Math.abs((address == null ? "NovaCart" : address).hashCode());
        return 18 + stable % 28;
    }
}
