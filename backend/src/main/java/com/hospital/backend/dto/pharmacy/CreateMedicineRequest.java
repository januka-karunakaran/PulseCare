package com.hospital.backend.dto.pharmacy;

import com.hospital.backend.enums.DosageForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMedicineRequest {

    @NotBlank(message = "Medicine code is required")
    private String medicineCode;

    @NotBlank(message = "Generic name is required")
    private String genericName;

    private String brandName;

    @NotBlank(message = "Strength is required (e.g. 500mg)")
    private String strength;

    @NotNull(message = "Dosage form is required")
    private DosageForm dosageForm;

    private String packageDescription;

    private String manufacturer;

    @NotBlank(message = "Dispensing unit is required (e.g. tablet, vial)")
    private String dispensingUnit;

    @PositiveOrZero(message = "Reorder level must be >= 0")
    private Double reorderLevel;
}
