package com.hospital.backend.dto.clinical;

import com.hospital.backend.enums.AllergyStatus;
import com.hospital.backend.model.PatientClinicalProfile;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateClinicalProfileRequest {
    private AllergyStatus allergyStatus;
    private List<PatientClinicalProfile.AllergyRecord> allergies;
    private List<String> pastMedicalHistory;
    private List<String> pastSurgicalHistory;
    private List<String> chronicConditions;
    private List<String> familyMedicalHistory;
    private List<String> socialHistory;
    private List<String> currentMedications;
    private Long expectedVersion;
}
