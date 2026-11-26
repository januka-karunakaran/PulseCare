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
public class TransferRequestDTO {

    @NotBlank(message = "Destination bed ID is mandatory")
    private String destinationBedId;

    @NotBlank(message = "Transfer reason code is mandatory")
    private String reasonCode;

    private String notes;

    private String idempotencyKey;
}
