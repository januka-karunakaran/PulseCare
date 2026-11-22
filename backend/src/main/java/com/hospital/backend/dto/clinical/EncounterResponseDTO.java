package com.hospital.backend.dto.clinical;

import com.hospital.backend.enums.EncounterStatus;
import com.hospital.backend.enums.EncounterType;
import com.hospital.backend.model.ClinicalNoteRevision;
import com.hospital.backend.model.ClinicalObservation;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterResponseDTO {
    private String id;
    private String appointmentId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private EncounterType encounterType;
    private EncounterStatus status;
    private Instant visitStartedAt;
    private Instant visitCompletedAt;
    private String chiefComplaint;
    private String historyOfPresentIllness;
    private String clinicalSummary;
    private Instant signedAt;
    private String signedByDoctorName;
    private Long version;
    private ClinicalNoteRevision currentNote;
    private List<ClinicalNoteRevision> revisions;
    private List<ClinicalObservation> observations;
}
