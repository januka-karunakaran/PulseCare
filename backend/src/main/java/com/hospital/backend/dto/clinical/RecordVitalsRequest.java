package com.hospital.backend.dto.clinical;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordVitalsRequest {
    private String encounterId;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer heartRate;
    private Double temperature;
    private Integer respiratoryRate;
    private Integer oxygenSaturation;
    private Double weightKg;
    private Double heightCm;
    private Integer painScore;
    private String clinicianNotes;
}
