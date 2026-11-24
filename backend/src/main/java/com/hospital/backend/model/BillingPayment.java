package com.hospital.backend.model;

import com.hospital.backend.enums.PaymentMethod;
import com.hospital.backend.enums.PaymentStatus;
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
import java.math.RoundingMode;
import java.time.Instant;

@Document(collection = "billing_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_pay_inv_date_idx", def = "{'invoiceId': 1, 'confirmedAt': -1}"),
        @CompoundIndex(name = "billing_pay_pat_date_idx", def = "{'patientId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "billing_pay_status_date_idx", def = "{'status': 1, 'createdAt': -1}")
})
public class BillingPayment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String paymentNumber;

    private String invoiceId;

    private String patientId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    @Field(targetType = FieldType.DECIMAL128)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal refundableAmount;

    @Builder.Default
    private String currency = "LKR";

    private PaymentMethod method;

    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    private String externalReference;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    private Instant receivedAt;

    private Instant confirmedAt;

    private String confirmedByUserId;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
