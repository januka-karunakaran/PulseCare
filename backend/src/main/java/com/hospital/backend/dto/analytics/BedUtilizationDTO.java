package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BedUtilizationDTO {
    private long totalBeds;
    private long availableBeds;
    private long occupiedBeds;
    private long maintenanceBeds;
    private double occupancyRate;
    private Map<String, Long> statusDistribution;
    private Map<String, Long> typeDistribution;
    private Map<String, Long> wardDistribution;
    private String historicalOccupancyStatus; // "CURRENT_SNAPSHOT_AVAILABLE" (Historical event log unavailable)
    private Instant generatedAt;
}
