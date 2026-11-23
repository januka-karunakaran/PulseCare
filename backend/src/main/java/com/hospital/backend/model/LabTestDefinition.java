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

import java.time.Instant;

@Document(collection = "lab_test_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "lab_test_active_category_idx", def = "{'active': 1, 'category': 1}")
})
public class LabTestDefinition {

    @Id
    private String id;

    @Indexed(unique = true)
    private String testCode;

    private String testName;

    private String category;

    private String description;

    private String specimenType;

    private String sampleVolume;

    private String collectionInstructions;

    private String defaultUnit;

    private String referenceRangeMetadata;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;
}
