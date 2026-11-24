package com.hospital.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSummaryDTO {

    private long totalInvoices;
    private long draftInvoices;
    private long issuedInvoices;
    private long partiallyPaidInvoices;
    private long paidInvoices;
    private long voidInvoices;
    private BigDecimal totalBilledAmount;
    private BigDecimal totalCollectedAmount;
    private BigDecimal totalOutstandingAmount;
    private String currency;
}
