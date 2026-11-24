package com.hospital.backend.dto.billing;

import com.hospital.backend.enums.PaymentMethod;
import com.hospital.backend.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    private String id;
    private String paymentNumber;
    private String invoiceId;
    private String invoiceNumber;
    private String patientId;
    private String patientName;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    private String externalReference;
    private Instant receivedAt;
    private Instant confirmedAt;
    private String confirmedByUserName;
    private String notes;
    private BigDecimal remainingInvoiceBalance;
}
