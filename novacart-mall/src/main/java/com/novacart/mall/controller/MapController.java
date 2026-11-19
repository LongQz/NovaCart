package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.service.MapEstimateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Delivery ETA endpoint based on configurable HttpClient. */
@RestController
@RequestMapping("/api/map")
public class MapController {
    private final MapEstimateService mapEstimateService;

    public MapController(MapEstimateService mapEstimateService) {
        this.mapEstimateService = mapEstimateService;
    }

    @GetMapping("/estimate")
    public ApiResponse<Integer> estimate(@RequestParam String address) {
        return ApiResponse.ok(mapEstimateService.estimateMinutes(address));
    }
}
