package com.hospital.backend.controller;

import com.hospital.backend.dto.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Enterprise Health Check Endpoint for Docker, Kubernetes and uptime monitors.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "service", "Hospital Management System Backend",
                "timestamp", Instant.now().toString(),
                "environment", "Production-Ready Enterprise"
        );
        return ResponseEntity.ok(ApiResponse.success("Service is healthy and operational", status));
    }
}
