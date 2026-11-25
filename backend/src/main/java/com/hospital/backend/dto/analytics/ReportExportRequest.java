package com.hospital.backend.dto.analytics;

import com.hospital.backend.enums.ReportFormat;
import com.hospital.backend.enums.ReportType;
import com.hospital.backend.enums.ReportingDateRange;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportExportRequest {
    @NotNull
    private ReportType reportType;
    @NotNull
    private ReportFormat format;
    @Builder.Default
    private ReportingDateRange dateRange = ReportingDateRange.LAST_30_DAYS;
    private LocalDate startDate;
    private LocalDate endDate;
    private String doctorId;
    @Builder.Default
    private String timezone = "Asia/Colombo";
}
