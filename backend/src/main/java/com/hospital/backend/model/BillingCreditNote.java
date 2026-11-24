package com.hospital.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "billing_credit_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_cn_inv_idx", def = "{'invoiceId': 1, 'issuedAt': -1}"),
        @CompoundIndex(name = "billing_cn_pat_idx", def = "{'patientId': 1, 'issuedAt': -1}")
})
public class BillingCreditNote {

    @Id
    private String id;

    @Indexed(unique = true)
    private String creditNoteNumber;

    private String invoiceId;

    private String patientId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    @Builder.Default
    private String currency = "LKR";

    private String reasonCode;

    private String notes;

    private String issuedByUserId;

    private Instant issuedAt;

    private Instant createdAt;

    @Version
    private Long version;
}
