package com.hospital.backend.dto.pharmacy;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiveStockRequest {

    @NotBlank(message = "Medicine ID is required")
    private String medicineId;

    @NotBlank(message = "Batch number is required")
    private String batchNumber;

    private String supplierReference;

    private Instant manufacturedAt;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private Instant expiryDate;

    @NotNull(message = "Quantity received is required")
    @Positive(message = "Quantity received must be positive")
    private Double quantityReceived;

    @NotBlank(message = "Stock unit is required")
    private String stockUnit;

    private String storageLocation;

    private String idempotencyKey;
}
