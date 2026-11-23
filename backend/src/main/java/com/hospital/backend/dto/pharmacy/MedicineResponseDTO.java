package com.hospital.backend.dto.pharmacy;

import com.hospital.backend.enums.DosageForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineResponseDTO {

    private String id;
    private String medicineCode;
    private String genericName;
    private String brandName;
    private String strength;
    private DosageForm dosageForm;
    private String packageDescription;
    private String manufacturer;
    private String dispensingUnit;
    private boolean active;
    private Double reorderLevel;
    private Double totalAvailableStock;
    private int batchCount;
    private boolean lowStock;
    private Instant createdAt;
    private Instant updatedAt;
}
