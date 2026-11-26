package com.hospital.backend.dto.bed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DischargeRequestDTO {
    private String notes;
    private boolean clinicalClearance;
    private String idempotencyKey;
}
