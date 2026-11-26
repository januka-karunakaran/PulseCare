package com.hospital.backend.dto.bed;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionRequestDTO {

    @NotBlank(message = "Patient ID is mandatory")
    private String patientId;

    private String doctorId; // Optional; if omitted, defaults to authenticated doctor or patient's primary doctor

    @NotBlank(message = "Bed ID is mandatory")
    private String bedId;

    private String admissionSource; // EMERGENCY, ELECTIVE, CLINICAL_REFERRAL, TRANSFER

    private String reasonCode;

    private String notes;

    private String clinicalEncounterId;

    private String idempotencyKey;
}
