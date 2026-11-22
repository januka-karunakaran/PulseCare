package com.hospital.backend.model;

import com.hospital.backend.enums.ClinicalAuditAction;
import com.hospital.backend.enums.Role;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "clinical_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "audit_patient_time_idx", def = "{'patientId': 1, 'occurredAt': -1}"),
    @CompoundIndex(name = "audit_actor_time_idx", def = "{'actorUserId': 1, 'occurredAt': -1}")
})
public class ClinicalAuditLog {

    @Id
    private String id;

    private String actorUserId;

    private String actorEmail;
    private Role actorRole;

    private String patientId;

    private String resourceType; // ENCOUNTER, NOTE, VITALS, PROFILE, TIMELINE
    private String resourceId;

    private ClinicalAuditAction action;

    @Indexed
    private Instant occurredAt;

    private String correlationId;
    private String outcome; // SUCCESS, DENIED, FAILED

    private String details; // Non-PHI metadata / summary only
}
