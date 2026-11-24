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

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "billing_refunds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_ref_pay_idx", def = "{'paymentId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "billing_ref_inv_idx", def = "{'invoiceId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "billing_ref_pat_idx", def = "{'patientId': 1, 'createdAt': -1}")
})
public class BillingRefund {

    @Id
    private String id;

    @Indexed(unique = true)
    private String refundNumber;

    private String paymentId;

    private String invoiceId;

    private String patientId;

    private BigDecimal amount;

    @Builder.Default
    private String currency = "LKR";

    private String reasonCode;

    private String externalReference;

    private String authorizedByUserId;

    private Instant createdAt;

    @Version
    private Long version;
}
