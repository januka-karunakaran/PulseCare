package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "clinical_observations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "obs_patient_date_idx", def = "{'patientId': 1, 'observedAt': -1}"),
    @CompoundIndex(name = "obs_encounter_date_idx", def = "{'encounterId': 1, 'observedAt': -1}")
})
public class ClinicalObservation {

    @Id
    private String id;

    private String encounterId;

    private String patientId;

    private Instant observedAt;

    private String recordedByUserId;
    private String recordedByName;

    // Vitals
    private Integer systolicBp;        // mmHg (40 - 300)
    private Integer diastolicBp;       // mmHg (20 - 200)
    private Integer heartRate;         // bpm (20 - 300)
    private Double temperature;        // °C (30.0 - 45.0)
    private Integer respiratoryRate;   // breaths/min (5 - 100)
    private Integer oxygenSaturation;  // % (50 - 100)
    private Double weightKg;           // kg (0.5 - 500.0)
    private Double heightCm;           // cm (20.0 - 300.0)
    private Integer painScore;         // 0 - 10

    private String clinicianNotes;

    @Version
    private Long version;
}
