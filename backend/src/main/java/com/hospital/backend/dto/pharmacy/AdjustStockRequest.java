package com.hospital.backend.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustStockRequest {

    @NotBlank(message = "Batch ID is required")
    private String batchId;

    @NotNull(message = "Adjusted quantity is required")
    private Double adjustedQuantity;

    @NotBlank(message = "Reason code is required (e.g. DAMAGED, AUDIT_CORRECTION, EXPIRED)")
    private String reasonCode;

    private String notes;
}
