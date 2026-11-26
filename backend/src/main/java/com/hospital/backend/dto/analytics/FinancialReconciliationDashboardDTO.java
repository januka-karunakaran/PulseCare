package com.hospital.backend.dto.analytics;

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
public class FinancialReconciliationDashboardDTO {
    private long totalInconsistenciesDetected;
    private long totalInvoicesAudited;
    private long totalPaymentsAudited;
    private long totalRefundsAudited;

    private long examinedRecordsCount;
    private long returnedAnomaliesCount;
    private boolean truncated;
    private boolean hasMore;
    private int page;
    private int pageSize;

    private List<ReconciliationAnomalyItem> anomalies;
    private Instant auditedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReconciliationAnomalyItem {
        private String anomalyType;
        private String severity; // HIGH, MEDIUM, LOW
        private String referenceEntity; // INVOICE, PAYMENT, REFUND
        private String entityId;
        private String referenceNumber;
        private String description;
        private BigDecimal expectedAmount;
        private BigDecimal actualAmount;
        private Instant detectedAt;
    }
}
