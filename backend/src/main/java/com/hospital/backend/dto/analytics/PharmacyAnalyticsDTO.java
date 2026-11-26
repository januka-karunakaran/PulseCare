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

    /**
     * @deprecated Retained for backwards compatibility with legacy clients; represents count of available batches.
     * Use {@link #getStockByUnit()} for unit-safe inventory quantities and {@link #getAvailableBatchesCount()}
     * for batch availability to avoid summing incompatible units (e.g. TABLETS + VIALS + ML).
     */
    @Deprecated
    private Long availableMedicineStock;

    private long availableBatchesCount;
    private long distinctMedicinesInStock;
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
