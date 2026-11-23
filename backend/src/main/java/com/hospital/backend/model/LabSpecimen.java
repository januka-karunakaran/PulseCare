package com.hospital.backend.model;

import com.hospital.backend.enums.SpecimenStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "lab_specimens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "lab_specimen_order_status_idx", def = "{'labOrderId': 1, 'collectionStatus': 1}"),
        @CompoundIndex(name = "lab_specimen_patient_idx", def = "{'patientId': 1}")
})
public class LabSpecimen {

    @Id
    private String id;

    @Indexed(unique = true)
    private String specimenNumber;

    private String labOrderId;

    private String patientId;

    private String specimenType;

    private SpecimenStatus collectionStatus;

    private Instant collectedAt;

    private String collectedByUserId;

    private Instant receivedAt;

    private String receivedByUserId;

    private Instant rejectedAt;

    private String rejectedByUserId;

    private String rejectionReason;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
