package com.hospital.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Hospital Ward entity representing physical and organizational hospital inpatient wings.
 */
@Document(collection = "hospital_wards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalWard {

    @Id
    private String id;

    @Indexed(unique = true)
    private String wardCode; // e.g. "WARD-GEN-01", "WARD-ICU-A"

    private String wardName;

    private String wardType; // GENERAL, ICU, SEMI_PRIVATE, PRIVATE, PEDIATRIC, SURGICAL

    private String description;

    @Builder.Default
    private boolean active = true;

    private Integer totalBeds;

    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
