package com.hospital.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationReportDTO {

    private long uninvoicedChargesCount;
    private long inconsistentInvoiceBalancesCount;
    private long missingLedgerEntriesCount;
    private List<String> warningDetails;
    private boolean healthy;
}
