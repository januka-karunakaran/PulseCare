package com.hospital.backend.dto.lab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLabTestRequest {
    private String testName;
    private String category;
    private String description;
    private String specimenType;
    private String sampleVolume;
    private String collectionInstructions;
    private String defaultUnit;
    private String referenceRangeMetadata;
    private Boolean active;
}
