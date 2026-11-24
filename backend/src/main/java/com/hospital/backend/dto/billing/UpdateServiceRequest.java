package com.hospital.backend.dto.billing;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateServiceRequest {

    private String serviceName;

    private String description;

    @DecimalMin(value = "0.0", message = "Unit price must be >= 0")
    private BigDecimal unitPrice;

    private String taxCategory;

    private Boolean active;
}
