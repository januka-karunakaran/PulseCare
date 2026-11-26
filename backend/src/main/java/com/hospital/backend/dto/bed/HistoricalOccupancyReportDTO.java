package com.hospital.backend.dto.bed;

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
public class HistoricalOccupancyReportDTO {
    private Instant periodStart;
    private Instant periodEnd;
    private String timeZone;
    private String filterWardId;
    private String filterBedType;

    private double totalOccupiedBedHours;
    private double totalAvailableBedHours;
    private double occupancyRatePercentage;

    private long admissionsCount;
    private long dischargesCount;
    private long transfersCount;
    private double averageLengthOfStayHours;

    private Map<String, Double> wardUtilizationBedHours;
    private Map<String, Double> bedTypeUtilizationBedHours;

    private List<DailyOccupancyMetricDTO> dailyTimeline;

    private String dataCompletenessStatus; // COMPLETE, PARTIAL_HISTORY, CURRENT_SNAPSHOT_ONLY, UNAVAILABLE
    private String completenessNotice;
    private Instant generatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyOccupancyMetricDTO {
        private String date; // YYYY-MM-DD
        private double occupiedBedHours;
        private double availableBedHours;
        private double occupancyRatePercentage;
        private long admissions;
        private long discharges;
        private long transfers;
    }
}
