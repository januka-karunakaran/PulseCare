package com.hospital.backend.controller;

import com.hospital.backend.dto.MedicalRecordDTO;
import com.hospital.backend.dto.clinical.*;
import com.hospital.backend.model.ClinicalObservation;
import com.hospital.backend.model.PatientClinicalProfile;
import com.hospital.backend.service.ClinicalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clinical")
@RequiredArgsConstructor
public class ClinicalController {

    private final ClinicalService clinicalService;

    @PostMapping("/encounters")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<EncounterResponseDTO> startOrResumeEncounter(@Valid @RequestBody CreateEncounterRequest request) {
        return ResponseEntity.ok(clinicalService.startOrResumeEncounter(request));
    }

    @GetMapping("/encounters/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EncounterResponseDTO> getEncounterById(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getEncounterById(id));
    }

    @GetMapping("/patients/{id}/encounters")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#id)")
    public ResponseEntity<List<EncounterResponseDTO>> getPatientEncounters(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getPatientEncounters(id));
    }

    @PutMapping("/encounters/{id}/draft")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<EncounterResponseDTO> updateEncounterDraft(@PathVariable String id,
                                                                    @RequestBody UpdateEncounterDraftRequest request) {
        return ResponseEntity.ok(clinicalService.updateEncounterDraft(id, request));
    }

    @PutMapping("/encounters/{id}/soap")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<EncounterResponseDTO> saveSoapNoteDraft(@PathVariable String id,
                                                                  @RequestBody SaveSoapNoteDraftRequest request) {
        return ResponseEntity.ok(clinicalService.saveSoapNoteDraft(id, request));
    }

    @PostMapping("/encounters/{id}/sign")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<EncounterResponseDTO> signEncounter(@PathVariable String id,
                                                              @RequestBody SignEncounterRequest request) {
        return ResponseEntity.ok(clinicalService.signEncounter(id, request));
    }

    @PostMapping("/encounters/{id}/amend")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<EncounterResponseDTO> amendEncounter(@PathVariable String id,
                                                               @Valid @RequestBody AmendEncounterRequest request) {
        return ResponseEntity.ok(clinicalService.amendEncounter(id, request));
    }

    @PostMapping("/encounters/{id}/vitals")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ClinicalObservation> recordEncounterVitals(@PathVariable String id,
                                                                     @RequestBody RecordVitalsRequest request) {
        request.setEncounterId(id);
        return ResponseEntity.ok(clinicalService.recordVitals(request));
    }

    @GetMapping("/encounters/{id}/vitals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ClinicalObservation>> getEncounterVitals(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getEncounterVitals(id));
    }

    @GetMapping("/patients/{id}/vitals")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#id)")
    public ResponseEntity<List<ClinicalObservation>> getPatientVitals(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getPatientVitals(id));
    }

    @GetMapping("/patients/{id}/profile")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#id)")
    public ResponseEntity<PatientClinicalProfile> getPatientProfile(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getPatientProfile(id));
    }

    @PutMapping("/patients/{id}/profile")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<PatientClinicalProfile> updatePatientProfile(@PathVariable String id,
                                                                       @RequestBody UpdateClinicalProfileRequest request) {
        return ResponseEntity.ok(clinicalService.updatePatientProfile(id, request));
    }

    @GetMapping("/patients/{id}/timeline")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#id)")
    public ResponseEntity<List<MedicalRecordDTO>> getClinicalTimeline(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getClinicalTimeline(id));
    }
}
