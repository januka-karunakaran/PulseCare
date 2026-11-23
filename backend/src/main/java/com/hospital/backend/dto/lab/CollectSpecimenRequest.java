package com.hospital.backend.dto.lab;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectSpecimenRequest {
    @NotBlank(message = "Specimen type is required")
    private String specimenType;

    private String notes;
}
