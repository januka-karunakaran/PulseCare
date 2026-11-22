package com.hospital.backend.service;

import com.hospital.backend.dto.MedicalRecordDTO;
import com.hospital.backend.dto.clinical.*;
import com.hospital.backend.model.ClinicalObservation;
import com.hospital.backend.model.PatientClinicalProfile;

import java.util.List;

public interface ClinicalService {
    EncounterResponseDTO startOrResumeEncounter(CreateEncounterRequest request);
    EncounterResponseDTO getEncounterById(String id);
    List<EncounterResponseDTO> getPatientEncounters(String patientId);
    EncounterResponseDTO updateEncounterDraft(String id, UpdateEncounterDraftRequest request);
    EncounterResponseDTO saveSoapNoteDraft(String encounterId, SaveSoapNoteDraftRequest request);
    EncounterResponseDTO signEncounter(String encounterId, SignEncounterRequest request);
    EncounterResponseDTO amendEncounter(String encounterId, AmendEncounterRequest request);

    ClinicalObservation recordVitals(RecordVitalsRequest request);
    List<ClinicalObservation> getEncounterVitals(String encounterId);
    List<ClinicalObservation> getPatientVitals(String patientId);

    PatientClinicalProfile getPatientProfile(String patientId);
    PatientClinicalProfile updatePatientProfile(String patientId, UpdateClinicalProfileRequest request);

    List<MedicalRecordDTO> getClinicalTimeline(String patientId);
}
