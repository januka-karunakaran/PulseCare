package com.hospital.backend.model;

import com.hospital.backend.enums.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "billing_ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_ledg_inv_date_idx", def = "{'invoiceId': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "billing_ledg_pay_date_idx", def = "{'paymentId': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "billing_ledg_pat_date_idx", def = "{'patientId': 1, 'occurredAt': -1}")
})
public class BillingLedgerEntry {

    @Id
    private String id;

    @Indexed(unique = true)
    private String entryNumber;

    private String invoiceId;

    private String paymentId;

    private String patientId;

    private LedgerEntryType entryType;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    @Builder.Default
    private String currency = "LKR";

    private String referenceType;

    private String referenceId;

    private Instant occurredAt;

    private String actorUserId;

    private String correlationId;

    private String idempotencyKey;
}
