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

/**
 * Bed Transfer Audit Record.
 * Preserves the atomic transfer history between beds and hospital wards.
 */
@Document(collection = "bed_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "transfer_admission_idx", def = "{'admissionId': 1, 'transferredAt': -1}"),
    @CompoundIndex(name = "transfer_patient_idx", def = "{'patientId': 1, 'transferredAt': -1}"),
    @CompoundIndex(name = "transfer_source_bed_idx", def = "{'sourceBedId': 1, 'transferredAt': -1}"),
    @CompoundIndex(name = "transfer_dest_bed_idx", def = "{'destinationBedId': 1, 'transferredAt': -1}")
})
public class BedTransferRecord {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transferNumber; // e.g. "TRF-20261126-XXXX"

    @Indexed
    private String admissionId;

    @Indexed
    private String patientId;

    private String sourceBedId;
    private String sourceWardId;

    private String destinationBedId;
    private String destinationWardId;

    private Instant transferredAt;

    private String actorUserId;

    private String reasonCode;

    private String notes;

    private Instant createdAt;

    @Indexed(sparse = true)
    private String idempotencyKey;
}
