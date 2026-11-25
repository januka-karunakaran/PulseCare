package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LaboratoryAnalyticsDTO {
    private long totalOrders;
    private long pendingCollectionOrders;
    private long specimensCollected;
    private long specimensRejected;
    private long resultsEntered;
    private long resultsVerified;
    private long resultsReleased;
    private long pendingVerificationQueue;
    private double averageTurnaroundMinutes; // Collection to Release
    private Map<String, Long> ordersByPriority;
    private Map<String, Long> resultsByInterpretation;
    private Instant generatedAt;
}
