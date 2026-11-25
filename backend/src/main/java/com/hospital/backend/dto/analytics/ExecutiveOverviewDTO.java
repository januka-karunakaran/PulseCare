package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutiveOverviewDTO {
    // Patient KPIs
    private long totalRegisteredPatients;
    private Long newlyRegisteredPatients; // Nullable if historical registration timestamp unavailable
    private long activePatientsPeriod;
    private String registrationTimestampStatus; // "TIMESTAMPS_AVAILABLE" or "HISTORICAL_TIMESTAMPS_NOT_AVAILABLE"

    // Doctor KPIs
    private long totalDoctors;
    private long activeDoctors;

    // Appointment KPIs
    private long appointmentsToday;
    private long appointmentsPeriod;
    private long completedAppointmentsPeriod;
    private long cancelledAppointmentsPeriod;
    private long pendingAppointmentsPeriod;
    private double appointmentCompletionRate;

    // Bed KPIs
    private long totalBeds;
    private long occupiedBeds;
    private long availableBeds;
    private long maintenanceBeds;
    private double bedOccupancyRate;

    // Laboratory KPIs
    private long totalLabOrdersPeriod;
    private long pendingLabOrders;
    private long specimensCollectedPeriod;
    private long releasedLabResultsPeriod;

    // Pharmacy KPIs
    private long activeMedicineCatalogCount;
    private long lowStockMedicineCount;
    private long nearExpiryBatchCount;
    private long expiredBatchCount;
    private long totalDispensesPeriod;

    // Financial KPIs
    private BigDecimal grossBilledAmount;
    private BigDecimal confirmedCollections;
    private BigDecimal outstandingReceivables;
    private BigDecimal totalRefunds;
    private String currency;

    private Instant generatedAt;
    private String timezone;
    private String dateRangeDescription;
}
