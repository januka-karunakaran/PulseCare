package com.hospital.backend.dto.lab;

import com.hospital.backend.enums.LabResultInterpretation;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectLabResultRequest {
    @NotBlank(message = "Correction reason is mandatory for clinical audit")
    private String correctionReason;

    private Double numericValue;
    private String textValue;
    private String unit;
    private String referenceRange;
    private LabResultInterpretation interpretation;
    private String notes;
}
