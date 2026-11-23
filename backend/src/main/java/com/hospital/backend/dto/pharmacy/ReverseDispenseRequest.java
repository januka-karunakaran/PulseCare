package com.hospital.backend.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReverseDispenseRequest {

    @NotBlank(message = "Dispense ID is required")
    private String dispenseId;

    @NotBlank(message = "Reason code is required (e.g. PATIENT_RETURN, DISPENSING_ERROR, ADVERSE_REACTION)")
    private String reasonCode;

    private String notes;
}
