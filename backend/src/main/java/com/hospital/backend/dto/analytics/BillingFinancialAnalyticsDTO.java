package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingFinancialAnalyticsDTO {
    private long totalInvoicesIssued;
    private BigDecimal grossBilledAmount;
    private BigDecimal totalDiscounts;
    private BigDecimal totalTax;
    private BigDecimal confirmedCollections;
    private BigDecimal outstandingReceivables;
    private BigDecimal totalRefunds;
    private BigDecimal totalCreditNotes;
    private String currency;

    private Map<String, BigDecimal> collectionsByMethod;
    private Map<String, Long> invoiceStatusDistribution;
    private List<DailyRevenuePoint> dailyRevenueTrends;
    private List<AgingBucketPoint> receivablesAging;

    private Instant generatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyRevenuePoint {
        private String date; // YYYY-MM-DD
        private BigDecimal billedAmount;
        private BigDecimal collectedAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgingBucketPoint {
        private String bucket; // 0-30 days, 31-60 days, 61-90 days, 90+ days
        private BigDecimal amount;
        private long invoiceCount;
    }
}
