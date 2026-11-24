package com.hospital.backend.dto.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvoiceDraftRequest {

    @NotBlank(message = "Patient ID is required")
    private String patientId;

    @NotEmpty(message = "At least one billable charge ID is required")
    private List<String> chargeIds;

    @DecimalMin(value = "0.0", message = "Discount cannot be negative")
    private BigDecimal discountAmount;

    private String discountReason;

    private String idempotencyKey;

    private String notes;
}
