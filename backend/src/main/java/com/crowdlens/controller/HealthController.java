package com.crowdlens.controller;

import com.crowdlens.provider.PlatformRegistry;
import com.crowdlens.service.AIAnalysisEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3001" })
@Tag(name = "Health", description = "Service health and connectivity checks")
public class HealthController {

    private final PlatformRegistry platformRegistry;
    private final AIAnalysisEngine aiEngine;

    @Operation(summary = "Check service health", description = "Returns the backend status, AI model connectivity, and status for each registered platform (Reddit, etc.)")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Platform health
        Map<String, Boolean> platformHealth = platformRegistry.healthCheckAll();
        boolean allPlatformsUp = platformHealth.values().stream().allMatch(v -> v);

        // AI health
        AIAnalysisEngine.HealthStatus aiHealth = aiEngine.healthCheck();

        // Overall status
        boolean overallHealthy = allPlatformsUp && aiHealth.healthy();

        status.put("status", overallHealthy ? "UP" : "DEGRADED");
        status.put("service", "crowdlens-backend");
        status.put("timestamp", Instant.now().toString());

        // Platforms section
        Map<String, Object> platforms = new LinkedHashMap<>();
        platformHealth.forEach((name, healthy) -> platforms.put(name, Map.of("status", healthy ? "UP" : "DOWN")));
        status.put("platforms", platforms);

        // AI section
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("status", aiHealth.healthy() ? "UP" : "DOWN");
        ai.put("message", aiHealth.message());
        if (aiHealth.hint() != null) {
            ai.put("hint", aiHealth.hint());
        }
        status.put("ai", ai);

        return ResponseEntity.ok(status);
    }
}
