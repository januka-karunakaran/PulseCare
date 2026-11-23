package com.hospital.backend.model;

import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.enums.OrderPriority;
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
import java.util.ArrayList;
import java.util.List;

@Document(collection = "lab_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "lab_order_patient_date_idx", def = "{'patientId': 1, 'orderedAt': -1}"),
        @CompoundIndex(name = "lab_order_doctor_date_idx", def = "{'doctorId': 1, 'orderedAt': -1}"),
        @CompoundIndex(name = "lab_order_encounter_idx", def = "{'encounterId': 1}"),
        @CompoundIndex(name = "lab_order_status_priority_idx", def = "{'status': 1, 'priority': 1}")
})
public class LabOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderNumber;

    private String patientId;

    private String doctorId;

    private String encounterId;

    private String appointmentId;

    @Builder.Default
    private List<OrderedTestItem> orderedTests = new ArrayList<>();

    private OrderPriority priority;

    private String clinicalNotes;

    private LabOrderStatus status;

    private Instant orderedAt;

    private String orderedByUserId;

    private Instant cancelledAt;

    private String cancelledByUserId;

    private String cancellationReason;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderedTestItem {
        private String testCode;
        private String testName;
        private String category;
        private String specimenType;
        private String defaultUnit;
        private String referenceRange;
    }
}
