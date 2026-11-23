package com.hospital.backend.dto.pharmacy;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMedicineRequest {

    private String brandName;

    private String packageDescription;

    private String manufacturer;

    private String dispensingUnit;

    private Boolean active;

    @PositiveOrZero(message = "Reorder level must be >= 0")
    private Double reorderLevel;
}
