package com.hospital.backend.dto.billing;

import com.hospital.backend.enums.ServiceCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateServiceRequest {

    @NotBlank(message = "Service code is required")
    private String serviceCode;

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotNull(message = "Service category is required")
    private ServiceCategory category;

    private String description;

    @Builder.Default
    private String currency = "LKR";

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.0", message = "Unit price must be >= 0")
    private BigDecimal unitPrice;

    @Builder.Default
    private String taxCategory = "STANDARD";
}
