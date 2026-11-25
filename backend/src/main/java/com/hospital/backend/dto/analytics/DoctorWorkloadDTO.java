package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorWorkloadDTO {
    private String doctorId;
    private String doctorName;
    private String specialization;
    private long totalAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private long encountersStarted;
    private long encountersFinalized;
    private long pendingDocumentationDrafts;
    private List<AppointmentAnalyticsDTO.DailyMetricPoint> dailyTrends;
    private Instant generatedAt;
}
