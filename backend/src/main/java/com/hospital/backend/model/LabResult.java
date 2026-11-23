package com.hospital.backend.model;

import com.hospital.backend.enums.LabResultInterpretation;
import com.hospital.backend.enums.LabResultWorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "lab_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "lab_result_order_test_idx", def = "{'labOrderId': 1, 'testCode': 1}"),
        @CompoundIndex(name = "lab_result_patient_released_idx", def = "{'patientId': 1, 'releasedAt': -1}"),
        @CompoundIndex(name = "lab_result_workflow_idx", def = "{'workflowStatus': 1, 'updatedAt': -1}")
})
public class LabResult {

    @Id
    private String id;

    // Legacy fields preserved for 100% backward compatibility
    private String appointmentId;
    private String patientId;
    private String doctorId;
    private String testName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private String status; // Legacy NORMAL, ABNORMAL interpretation string
    private String testDate;
    private String notes;

    // Structured Feature 02.5 fields
    private String labOrderId;
    private String specimenId;
    private String encounterId;
    private String testCode;
    private String resultType; // NUMERIC or TEXT
    private Double numericValue;
    private String textValue;
    private LabResultInterpretation interpretation; // NORMAL, ABNORMAL, CRITICAL, INDETERMINATE, NOT_ASSESSED
    private LabResultWorkflowStatus workflowStatus; // DRAFT, ENTERED, VERIFIED, RELEASED, CORRECTED

    private Instant measuredAt;
    private String enteredByUserId;
    private Instant enteredAt;

    private String verifiedByUserId;
    private Instant verifiedAt;

    private String releasedByUserId;
    private Instant releasedAt;

    private String correctedResultId;
    private String correctionReason;
    @Builder.Default
    private Integer revision = 1;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
