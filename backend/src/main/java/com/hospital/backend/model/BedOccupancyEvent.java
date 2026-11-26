package com.hospital.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable Append-Only Bed Occupancy Event Entity.
 * Preserves the authoritative history of all inpatient bed admissions, movements,
 * discharges, turnovers, and maintenance operations.
 */
@Document(collection = "bed_occupancy_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "event_bed_occurred_idx", def = "{'bedId': 1, 'occurredAt': 1}"),
    @CompoundIndex(name = "event_admission_seq_idx", def = "{'admissionId': 1, 'sequenceNumber': 1}"),
    @CompoundIndex(name = "event_ward_occurred_idx", def = "{'wardId': 1, 'occurredAt': 1}"),
    @CompoundIndex(name = "event_patient_occurred_idx", def = "{'patientId': 1, 'occurredAt': 1}"),
    @CompoundIndex(name = "event_type_occurred_idx", def = "{'eventType': 1, 'occurredAt': 1}")
})
public class BedOccupancyEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String admissionId;

    @Indexed
    private String patientId;

    @Indexed
    private String bedId;

    private String wardId;

    private String eventType; // ADMISSION_STARTED, BED_OCCUPIED, TRANSFER_STARTED, BED_VACATED, TRANSFER_COMPLETED, DISCHARGE_COMPLETED, BED_TURNOVER_STARTED, BED_AVAILABLE, BED_MAINTENANCE_STARTED, BED_MAINTENANCE_ENDED, CORRECTION_RECORDED

    private Instant occurredAt;

    private Instant recordedAt;

    private String actorUserId;

    private String reasonCode;

    private String sourceBedId;

    private String destinationBedId;

    private String correlationId;

    @Indexed(sparse = true)
    private String idempotencyKey;

    private Long sequenceNumber;

    private Map<String, Object> metadata;
}
