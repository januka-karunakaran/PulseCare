package com.hospital.backend.dto.pharmacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispenseMedicationRequest {

    private String prescriptionId;

    private String idempotencyKey;

    @NotEmpty(message = "At least one item must be dispensed")
    @Valid
    private List<DispenseItemRequest> items;

    private String notes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DispenseItemRequest {
        private String prescriptionLineId;

        @NotNull(message = "Medicine ID is required")
        private String medicineId;

        @NotNull(message = "Requested quantity is required")
        @Positive(message = "Requested quantity must be positive")
        private Double requestedQuantity;

        private String dispenseUnit;

        private String notes;
    }
}
