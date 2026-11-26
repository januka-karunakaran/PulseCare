package com.hospital.backend.dto.bed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Public/General availability representation of a hospital bed.
 * Contains ZERO sensitive patient PHI or active booking linkage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BedSummaryDTO {
    private String id;
    private String bedNumber;
    private String type; // GENERAL, ICU, SEMI_PRIVATE, PRIVATE
    private String status; // AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED, CLEANING
    private String ward;
    private String wardId;
    private String locationCode;
    private BigDecimal dailyRate;
    private Double pricePerDay;
    private boolean active;
}
