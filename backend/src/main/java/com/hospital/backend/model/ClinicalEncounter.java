package com.hospital.backend.model;

import com.hospital.backend.enums.EncounterStatus;
import com.hospital.backend.enums.EncounterType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "clinical_encounters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "encounter_patient_started_idx", def = "{'patientId': 1, 'visitStartedAt': -1}"),
    @CompoundIndex(name = "encounter_doc_status_idx", def = "{'doctorId': 1, 'status': 1}"),
    @CompoundIndex(name = "encounter_appt_idx", def = "{'appointmentId': 1}")
})
public class ClinicalEncounter {

    @Id
    private String id;

    private String appointmentId;

    private String patientId;

    private String doctorId;

    @Builder.Default
    private EncounterType encounterType = EncounterType.OUTPATIENT;

    @Builder.Default
    private EncounterStatus status = EncounterStatus.DRAFT;

    private Instant visitStartedAt;
    private Instant visitCompletedAt;

    private String chiefComplaint;
    private String historyOfPresentIllness;
    private String clinicalSummary;

    private String createdByUserId;
    private String updatedByUserId;

    private Instant createdAt;
    private Instant updatedAt;

    private Instant signedAt;
    private String signedByUserId;
    private String signedByDoctorName;

    @Version
    private Long version;
}
