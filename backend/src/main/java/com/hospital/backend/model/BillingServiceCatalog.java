package com.hospital.backend.model;

import com.hospital.backend.enums.ServiceCategory;
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

@Document(collection = "billing_service_catalog")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "billing_svc_cat_active_idx", def = "{'category': 1, 'active': 1}"),
        @CompoundIndex(name = "billing_svc_active_name_idx", def = "{'active': 1, 'serviceName': 1}")
})
public class BillingServiceCatalog {

    @Id
    private String id;

    @Indexed(unique = true)
    private String serviceCode;

    private String serviceName;

    private ServiceCategory category;

    private String description;

    @Builder.Default
    private String currency = "LKR";

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitPrice;

    @Builder.Default
    private String taxCategory = "STANDARD";

    @Builder.Default
    private boolean active = true;

    private Instant effectiveFrom;

    private Instant effectiveUntil;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
