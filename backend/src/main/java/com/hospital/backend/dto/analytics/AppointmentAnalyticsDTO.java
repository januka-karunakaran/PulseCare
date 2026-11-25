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
public class AppointmentAnalyticsDTO {
    private long totalAppointments;
    private long bookedAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private long pendingAppointments;
    private double completionRate;

    private List<DailyMetricPoint> dailyTrends;
    private Map<String, Long> statusDistribution;
    private List<DoctorWorkloadSummary> doctorWorkloads;

    private Instant generatedAt;
    private String timezone;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyMetricPoint {
        private String date; // YYYY-MM-DD
        private long total;
        private long completed;
        private long cancelled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DoctorWorkloadSummary {
        private String doctorId;
        private String doctorName;
        private String specialization;
        private long totalAppointments;
        private long completedAppointments;
        private double completionRate;
    }
}
