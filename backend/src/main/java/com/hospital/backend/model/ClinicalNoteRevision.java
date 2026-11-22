package com.hospital.backend.model;

import com.hospital.backend.enums.DiagnosisStatus;
import com.hospital.backend.enums.NoteStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "clinical_note_revisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "note_encounter_rev_idx", def = "{'encounterId': 1, 'revisionNumber': 1}"),
    @CompoundIndex(name = "note_patient_status_idx", def = "{'patientId': 1, 'status': 1}")
})
public class ClinicalNoteRevision {

    @Id
    private String id;

    private String encounterId;

    private String patientId;

    private String doctorId;

    @Builder.Default
    private Integer revisionNumber = 1;

    @Builder.Default
    private NoteStatus status = NoteStatus.DRAFT;

    // S - Subjective
    private String chiefComplaint;
    private String subjective;

    // O - Objective
    private String objective;
    private String vitalsSummary;

    // A - Assessment
    private String assessment;
    private String diagnosisText;
    private String diagnosisCode;
    private String codingSystem;
    private DiagnosisStatus diagnosisStatus;

    // P - Plan
    private String plan;
    private String testsRequested;
    private String followUpInstructions;
    private String followUpDate;
    private Boolean followUpRequired;
    private String referralNotes;

    // Amendment metadata
    private String amendmentReason;
    private String amendedByUserId;
    private Instant amendedAt;

    // Signature metadata
    private String signedByUserId;
    private String signedByDoctorName;
    private Instant signedAt;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
