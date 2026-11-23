package com.hospital.backend.dto.lab;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectSpecimenRequest {
    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
