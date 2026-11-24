package com.hospital.backend.dto.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessRefundRequest {

    @NotBlank(message = "Payment ID is required")
    private String paymentId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be > 0")
    private BigDecimal amount;

    @NotBlank(message = "Reason code is required (e.g. BILLING_ERROR, PATIENT_REQUEST, SERVICE_CANCELLED)")
    private String reasonCode;

    private String externalReference;

    private String notes;
}
