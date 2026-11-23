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
public class CreateLabTestRequest {
    @NotBlank(message = "Test code is required")
    private String testCode;

    @NotBlank(message = "Test name is required")
    private String testName;

    @NotBlank(message = "Category is required")
    private String category;

    private String description;

    @NotBlank(message = "Specimen type is required")
    private String specimenType;

    private String sampleVolume;
    private String collectionInstructions;
    private String defaultUnit;
    private String referenceRangeMetadata;
}
