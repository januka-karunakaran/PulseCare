package com.hospital.backend.dto.pharmacy;

import com.hospital.backend.enums.DispenseStatus;
import com.hospital.backend.model.PharmacyDispense;
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
public class DispenseResponseDTO {

    private String id;
    private String dispenseNumber;
    private String prescriptionId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private String pharmacistUserId;
    private String pharmacistName;
    private List<PharmacyDispense.DispenseLineItem> items;
    private DispenseStatus status;
    private Instant dispensedAt;
    private String notes;
    private Instant createdAt;
}
