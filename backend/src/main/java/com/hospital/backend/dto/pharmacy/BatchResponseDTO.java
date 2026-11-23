package com.hospital.backend.dto.pharmacy;

import com.hospital.backend.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchResponseDTO {

    private String id;
    private String medicineId;
    private String medicineName;
    private String batchNumber;
    private String supplierReference;
    private Instant receivedAt;
    private Instant manufacturedAt;
    private Instant expiryDate;
    private Double quantityReceived;
    private Double quantityAvailable;
    private Double quantityReserved;
    private String stockUnit;
    private String storageLocation;
    private BatchStatus status;
    private boolean nearExpiry;
    private boolean expired;
    private Instant createdAt;
}
