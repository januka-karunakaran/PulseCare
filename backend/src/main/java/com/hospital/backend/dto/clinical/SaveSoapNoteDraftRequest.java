package com.hospital.backend.dto.clinical;

import com.hospital.backend.enums.DiagnosisStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveSoapNoteDraftRequest {
    private String chiefComplaint;
    private String subjective;
    private String objective;
    private String assessment;
    private String diagnosisText;
    private String diagnosisCode;
    private String codingSystem;
    private DiagnosisStatus diagnosisStatus;
    private String plan;
    private String testsRequested;
    private String followUpInstructions;
    private String followUpDate;
    private Boolean followUpRequired;
    private String referralNotes;
    private Long expectedVersion;
}
