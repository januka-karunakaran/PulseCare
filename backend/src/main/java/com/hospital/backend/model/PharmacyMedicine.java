package com.hospital.backend.model;

import com.hospital.backend.enums.DosageForm;
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

import java.time.Instant;

@Document(collection = "pharmacy_medicines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "pharm_med_active_name_idx", def = "{'active': 1, 'genericName': 1}"),
        @CompoundIndex(name = "pharm_med_generic_strength_form_idx", def = "{'genericName': 1, 'strength': 1, 'dosageForm': 1}")
})
public class PharmacyMedicine {

    @Id
    private String id;

    @Indexed(unique = true)
    private String medicineCode;

    private String genericName;

    private String brandName;

    private String strength;

    private DosageForm dosageForm;

    private String packageDescription;

    private String manufacturer;

    private String dispensingUnit;

    private boolean active;

    private Double reorderLevel;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
