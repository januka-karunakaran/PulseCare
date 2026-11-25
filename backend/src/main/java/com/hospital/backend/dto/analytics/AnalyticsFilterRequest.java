package com.hospital.backend.dto.analytics;

import com.hospital.backend.enums.ReportingDateRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsFilterRequest {
    @Builder.Default
    private ReportingDateRange dateRange = ReportingDateRange.LAST_30_DAYS;
    private LocalDate startDate;
    private LocalDate endDate;
    private String doctorId;
    private String department;
    private String status;
    @Builder.Default
    private String timezone = "Asia/Colombo";
}
