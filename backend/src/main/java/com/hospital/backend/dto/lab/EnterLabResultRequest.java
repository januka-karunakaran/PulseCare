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
public class EnterLabResultRequest {
    @NotBlank(message = "Test code is required")
    private String testCode;

    private String specimenId;

    @NotBlank(message = "Result type is required (NUMERIC or TEXT)")
    private String resultType;

    private Double numericValue;
    private String textValue;
    private String unit;
    private String referenceRange;
    private LabResultInterpretation interpretation;
    private String notes;
    private boolean saveAsDraft;
}
