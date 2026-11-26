package com.hospital.backend.dto.bed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionResponseDTO {
    private String id;
    private String admissionNumber;
    private String patientId;
    private String patientName;
    private String patientEmail;
    private String doctorId;
    private String doctorName;
    private String bedId;
    private String bedNumber;
    private String ward;
    private String wardId;
    private String wardName;
    private String status;
    private Instant admittedAt;
    private Instant dischargedAt;
    private String admissionDate;
    private String dischargeDate;
    private String admissionSource;
    private String reasonCode;
    private String notes;
    private String clinicalEncounterId;
    private String clinicalClearanceByDoctorId;
    private Instant dischargeReadyAt;
    private Long stayDurationHours;
    private Instant createdAt;
    private Instant updatedAt;
}
