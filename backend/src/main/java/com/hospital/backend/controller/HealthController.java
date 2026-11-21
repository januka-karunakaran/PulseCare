package com.hospital.backend.controller;

import com.hospital.backend.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health Check Endpoints for Docker, Kubernetes and uptime monitors.
 * Provides separate liveness and readiness probes.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final MongoTemplate mongoTemplate;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        return checkLiveness();
    }

    @GetMapping("/liveness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkLiveness() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "service", "Hospital Management System Backend",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success("Service is live and operational", status));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkReadiness() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            Map<String, Object> status = Map.of(
                    "status", "UP",
                    "database", "UP",
                    "service", "Hospital Management System Backend",
                    "timestamp", Instant.now().toString()
            );
            return ResponseEntity.ok(ApiResponse.success("Service and dependencies are ready", status));
        } catch (Exception e) {
            log.error("Readiness probe failed - MongoDB unreachable: {}", e.getMessage());
            Map<String, Object> status = Map.of(
                    "status", "DOWN",
                    "database", "DOWN",
                    "service", "Hospital Management System Backend",
                    "timestamp", Instant.now().toString()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Service dependencies not ready", HttpStatus.SERVICE_UNAVAILABLE.value(), status));
        }
    }
}
