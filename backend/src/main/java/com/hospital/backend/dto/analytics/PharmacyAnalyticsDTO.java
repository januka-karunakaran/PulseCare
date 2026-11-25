package com.hospital.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyAnalyticsDTO {
    private long activeMedicineCatalogCount;
    private long availableMedicineStock;
    private Map<String, Double> stockByUnit;
    private long lowStockItemsCount;
    private long nearExpiryBatchesCount;
    private long expiredBatchesCount;
    private long quarantinedBatchesCount;
    private long totalDispenses;
    private long partiallyDispensedCount;
    private double totalUnitsDispensed;
    private String inventoryValuationStatus; // "COST_BASIS_NOT_AVAILABLE"
    private Instant generatedAt;
}
