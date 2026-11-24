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
public class IssueCreditNoteRequest {

    @NotBlank(message = "Invoice ID is required")
    private String invoiceId;

    @NotNull(message = "Credit note amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be > 0")
    private BigDecimal amount;

    @NotBlank(message = "Reason code is required")
    private String reasonCode;

    private String notes;
}
