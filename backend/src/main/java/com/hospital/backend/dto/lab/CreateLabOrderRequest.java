package com.hospital.backend.dto.lab;

import com.hospital.backend.enums.OrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLabOrderRequest {
    @NotBlank(message = "Patient ID is required")
    private String patientId;

    private String encounterId;
    private String appointmentId;

    @NotEmpty(message = "At least one test must be ordered")
    private List<String> testCodes;

    @Builder.Default
    private OrderPriority priority = OrderPriority.ROUTINE;

    private String clinicalNotes;
    private String idempotencyKey;
}
