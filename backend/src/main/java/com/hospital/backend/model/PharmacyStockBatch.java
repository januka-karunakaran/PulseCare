package com.hospital.backend.model;

import com.hospital.backend.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "pharmacy_stock_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "pharm_batch_fefo_idx", def = "{'medicineId': 1, 'expiryDate': 1, 'status': 1}"),
        @CompoundIndex(name = "pharm_batch_med_num_idx", def = "{'medicineId': 1, 'batchNumber': 1}"),
        @CompoundIndex(name = "pharm_batch_status_exp_idx", def = "{'status': 1, 'expiryDate': 1}")
})
public class PharmacyStockBatch {

    @Id
    private String id;

    private String medicineId;

    private String batchNumber;

    private String supplierReference;

    private Instant receivedAt;

    private Instant manufacturedAt;

    private Instant expiryDate;

    private Double quantityReceived;

    private Double quantityAvailable;

    @Builder.Default
    private Double quantityReserved = 0.0;

    private String stockUnit;

    private String storageLocation;

    private BatchStatus status;

    private String idempotencyKey;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
