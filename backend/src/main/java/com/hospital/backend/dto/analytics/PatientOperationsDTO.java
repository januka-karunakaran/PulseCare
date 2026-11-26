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
public class PatientOperationsDTO {
    private long totalRegisteredPatients;
    private Long newRegistrationsPeriod;
    private String registrationTimestampStatus; // "COMPLETE", "PARTIAL_HISTORICAL_DATA", or "UNAVAILABLE"
    private long patientsWithConsultations;
    private Map<String, Long> genderDistribution;
    private Map<String, Long> bloodGroupDistribution;
    private List<AppointmentAnalyticsDTO.DailyMetricPoint> registrationTrends;
    private Instant generatedAt;
}
