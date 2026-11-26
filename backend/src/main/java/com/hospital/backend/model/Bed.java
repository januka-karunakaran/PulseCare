package com.hospital.backend.model;

import lombok.*;
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

@Document(collection = "beds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "bed_ward_number_uniq", def = "{'ward': 1, 'bedNumber': 1}", unique = true),
    @CompoundIndex(name = "bed_status_ward_idx", def = "{'status': 1, 'wardId': 1}"),
    @CompoundIndex(name = "bed_type_status_idx", def = "{'type': 1, 'status': 1}")
})
public class Bed {
    @Id
    private String id;

    @Indexed
    private String bedNumber;

    private String type; // GENERAL, ICU, SEMI_PRIVATE, PRIVATE

    private String status; // AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED, CLEANING

    private Double pricePerDay;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal dailyRate;

    private String ward; // Legacy ward name

    private String wardId; // Canonical HospitalWard ID

    private String locationCode; // Room/Wing/Floor identifier

    private String currentBookingId; // Active admission/booking reference

    private String currentPatientId; // Active patient reference

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastStateChangedAt;

    @Version
    private Long version;

    public BigDecimal getAuthoritativeRate() {
        if (dailyRate != null && dailyRate.compareTo(BigDecimal.ZERO) > 0) {
            return dailyRate;
        }
        if (pricePerDay != null && pricePerDay > 0) {
            return BigDecimal.valueOf(pricePerDay);
        }
        return BigDecimal.ZERO;
    }
}
