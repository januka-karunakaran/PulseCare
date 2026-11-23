package com.hospital.backend.model;

import com.hospital.backend.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "pharmacy_stock_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "pharm_mov_batch_date_idx", def = "{'batchId': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "pharm_mov_med_date_idx", def = "{'medicineId': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "pharm_mov_ref_idx", def = "{'referenceType': 1, 'referenceId': 1}")
})
public class PharmacyStockMovement {

    @Id
    private String id;

    private String medicineId;

    private String batchId;

    private StockMovementType movementType;

    private Double quantity;

    private Double balanceAfter;

    private String unit;

    private String referenceType;

    private String referenceId;

    private String idempotencyKey;

    private String actorUserId;

    private String actorRole;

    private Instant occurredAt;

    private String reasonCode;

    private String correlationId;
}
