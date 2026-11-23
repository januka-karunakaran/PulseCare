package com.hospital.backend.dto.lab;

import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.enums.OrderPriority;
import com.hospital.backend.model.LabOrder;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.LabSpecimen;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrderResponseDTO {
    private String id;
    private String orderNumber;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private String encounterId;
    private String appointmentId;
    private List<LabOrder.OrderedTestItem> orderedTests;
    private OrderPriority priority;
    private String clinicalNotes;
    private LabOrderStatus status;
    private Instant orderedAt;
    private String orderedByUserId;
    private Instant cancelledAt;
    private String cancellationReason;
    private List<LabSpecimen> specimens;
    private List<LabResult> results;
    private Instant createdAt;
    private Instant updatedAt;
}
