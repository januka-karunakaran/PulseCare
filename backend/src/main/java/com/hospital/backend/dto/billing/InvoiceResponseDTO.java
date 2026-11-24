package com.hospital.backend.dto.billing;

import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.enums.PaymentMethod;
import com.hospital.backend.model.Invoice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDTO {

    private String id;
    private String invoiceNumber;
    private String patientId;
    private String patientName;
    private String currency;
    private List<Invoice.InvoiceItem> items;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private InvoiceStatus status;
    private PaymentMethod paymentMethod;
    private Instant issuedAt;
    private Instant dueAt;
    private Instant createdAt;
    private boolean overdue;
}
