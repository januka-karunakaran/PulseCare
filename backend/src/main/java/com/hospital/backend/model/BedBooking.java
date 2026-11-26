package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Enterprise Hospital Bed Booking & Inpatient Admission Lifecycle Entity.
 * Preserves full backward compatibility with legacy BedBooking records while
 * supporting robust admission lifecycle states, precise server Instants, and clinical integration.
 */
@Document(collection = "bed_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "booking_patient_admitted_idx", def = "{'patientId': 1, 'admittedAt': -1}"),
    @CompoundIndex(name = "booking_doctor_admitted_idx", def = "{'doctorId': 1, 'admittedAt': -1}"),
    @CompoundIndex(name = "booking_bed_status_idx", def = "{'bedId': 1, 'status': 1}"),
    @CompoundIndex(name = "booking_status_admitted_idx", def = "{'status': 1, 'admittedAt': -1}")
})
public class BedBooking {
    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String admissionNumber; // e.g. "ADM-20261126-A1B2C3"

    private String bedId;
    private String patientId;
    private String doctorId;

    private String admissionDate; // Legacy date string (YYYY-MM-DD)
    private String dischargeDate; // Legacy date string (YYYY-MM-DD)

    private Instant admittedAt; // Server-side authoritative admission Instant
    private Instant dischargedAt; // Server-side authoritative discharge Instant

    private String currentWardId;
    private String currentBedId;

    private String status; // ACTIVE, ADMITTED, TRANSFER_PENDING, DISCHARGE_READY, DISCHARGED, CANCELLED

    private String admissionSource; // EMERGENCY, ELECTIVE, CLINICAL_REFERRAL, TRANSFER
    private String reasonCode; // e.g. "POST_OP_RECOVERY", "ICU_MONITORING"
    private String notes;

    private String clinicalEncounterId;
    private String clinicalClearanceByDoctorId;
    private Instant dischargeReadyAt;

    private String createdByUserId;
    private String dischargedByUserId;

    @Indexed(sparse = true)
    private String idempotencyKey;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status) ||
               "ADMITTED".equalsIgnoreCase(status) ||
               "TRANSFER_PENDING".equalsIgnoreCase(status) ||
               "DISCHARGE_READY".equalsIgnoreCase(status);
    }
}
