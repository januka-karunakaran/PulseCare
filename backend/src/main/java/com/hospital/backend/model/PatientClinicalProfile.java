package com.hospital.backend.model;

import com.hospital.backend.enums.AllergySeverity;
import com.hospital.backend.enums.AllergyStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "patient_clinical_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientClinicalProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String patientId;

    @Builder.Default
    private AllergyStatus allergyStatus = AllergyStatus.UNKNOWN;

    @Builder.Default
    private List<AllergyRecord> allergies = new ArrayList<>();

    @Builder.Default
    private List<String> pastMedicalHistory = new ArrayList<>();

    @Builder.Default
    private List<String> pastSurgicalHistory = new ArrayList<>();

    @Builder.Default
    private List<String> chronicConditions = new ArrayList<>();

    @Builder.Default
    private List<String> familyMedicalHistory = new ArrayList<>();

    @Builder.Default
    private List<String> socialHistory = new ArrayList<>();

    @Builder.Default
    private List<String> currentMedications = new ArrayList<>();

    private Instant updatedAt;
    private String updatedByUserId;
    private String updatedByUserName;

    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllergyRecord {
        private String allergen;
        private String reaction;
        private AllergySeverity severity;
        private String verificationStatus; // SUSPECTED, VERIFIED, RESOLVED
        private Instant recordedAt;
        private String recordedBy;
    }
}
