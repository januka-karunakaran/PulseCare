package com.hospital.backend.model;

import com.hospital.backend.enums.ChargeSourceType;
import com.hospital.backend.enums.ChargeStatus;
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

@Document(collection = "billing_charges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_chg_source_uniq_idx", def = "{'sourceType': 1, 'sourceId': 1, 'sourceLineId': 1}", unique = true),
        @CompoundIndex(name = "billing_chg_pat_status_idx", def = "{'patientId': 1, 'chargeStatus': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "billing_chg_invoice_idx", def = "{'invoiceId': 1}")
})
public class BillingCharge {

    @Id
    private String id;

    @Indexed(unique = true)
    private String chargeNumber;

    private String patientId;

    private ChargeSourceType sourceType;

    private String sourceId;

    private String sourceLineId;

    private String serviceCode;

    private String descriptionSnapshot;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantity;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitPrice;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal lineSubtotal;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal taxSnapshot;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;

    @Builder.Default
    private String currency = "LKR";

    @Builder.Default
    private ChargeStatus chargeStatus = ChargeStatus.PENDING;

    private String invoiceId;

    private String createdByUserId;

    private String idempotencyKey;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
