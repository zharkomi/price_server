package com.price.query.controller;

import com.price.common.config.PriceConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final PriceConfiguration configuration;

    @GetMapping("/config")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "PriceQueryService",
                "instruments", configuration.instruments(),
                "timestamp", System.currentTimeMillis()
        );
    }
}
