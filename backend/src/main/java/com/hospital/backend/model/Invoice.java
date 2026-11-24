package com.hospital.backend.model;

import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.enums.PaymentMethod;
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
import java.util.ArrayList;
import java.util.List;

@Document(collection = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "inv_pat_issued_idx", def = "{'patientId': 1, 'issuedAt': -1}"),
        @CompoundIndex(name = "inv_status_due_idx", def = "{'status': 1, 'dueAt': 1}"),
        @CompoundIndex(name = "inv_status_created_idx", def = "{'status': 1, 'createdAt': -1}")
})
public class Invoice {

    @Id
    private String id;

    @Indexed(unique = true)
    private String invoiceNumber;

    private String patientId;

    private String patientName;

    private String currency = "LKR";

    private List<InvoiceItem> items = new ArrayList<>();

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal discount;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal tax;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount; // Grand total

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal outstandingAmount;

    private InvoiceStatus status = InvoiceStatus.DRAFT;

    private PaymentMethod paymentMethod;

    private Instant issuedAt;

    private Instant dueAt;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdByUserId;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @Version
    private Long version;

    // Legacy date string field compatibility
    private String date;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceItem {
        private String chargeId;
        private String serviceCode;
        private String description;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal quantity;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal unitPrice;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal lineSubtotal;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal discountAmount;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal taxAmount;
        @Field(targetType = FieldType.DECIMAL128)
        private BigDecimal total;
    }

    public static class InvoiceBuilder {
        private BigDecimal totalAmount;
        private InvoiceStatus status = InvoiceStatus.DRAFT;

        public InvoiceBuilder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount != null ? BigDecimal.valueOf(totalAmount).setScale(2, RoundingMode.HALF_UP) : null;
            return this;
        }
        public InvoiceBuilder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public InvoiceBuilder status(String status) {
            if (status == null) {
                this.status = null;
            } else if ("UNPAID".equalsIgnoreCase(status) || "ISSUED".equalsIgnoreCase(status)) {
                this.status = InvoiceStatus.ISSUED;
            } else if ("PAID".equalsIgnoreCase(status)) {
                this.status = InvoiceStatus.PAID;
            } else if ("VOID".equalsIgnoreCase(status)) {
                this.status = InvoiceStatus.VOID;
            } else if ("PARTIALLY_PAID".equalsIgnoreCase(status)) {
                this.status = InvoiceStatus.PARTIALLY_PAID;
            } else {
                try {
                    this.status = InvoiceStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    this.status = InvoiceStatus.ISSUED;
                }
            }
            return this;
        }

        public InvoiceBuilder status(InvoiceStatus status) {
            this.status = status;
            return this;
        }
    }
}
