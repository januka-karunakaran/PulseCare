package com.hospital.backend.service;

import com.hospital.backend.enums.*;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class LabMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_lab_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                mongoDBContainer.start();
            }
        } catch (Throwable ignored) {
        }
    }

    @DynamicPropertySource
    static void setMongoProperties(DynamicPropertyRegistry registry) {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
        }
    }

    @Autowired private LabTestDefinitionRepository testDefinitionRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabSpecimenRepository labSpecimenRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(LabTestDefinition.class);
        mongoTemplate.indexOps(LabTestDefinition.class).ensureIndex(
                new Index().on("testCode", Sort.Direction.ASC).unique()
        );
        mongoTemplate.dropCollection(LabOrder.class);
        mongoTemplate.indexOps(LabOrder.class).ensureIndex(
                new Index().on("orderNumber", Sort.Direction.ASC).unique()
        );
        mongoTemplate.dropCollection(LabSpecimen.class);
        mongoTemplate.indexOps(LabSpecimen.class).ensureIndex(
                new Index().on("specimenNumber", Sort.Direction.ASC).unique()
        );
        mongoTemplate.dropCollection(LabResult.class);
    }

    @Test
    @DisplayName("Mongo Integration: Test catalog unique index prevents duplicate testCode")
    void testCatalog_UniqueIndex_RejectsDuplicates() {
        LabTestDefinition test1 = LabTestDefinition.builder()
                .testCode("LIPID")
                .testName("Lipid Panel")
                .category("Biochemistry")
                .specimenType("Serum")
                .active(true)
                .createdAt(Instant.now())
                .build();
        testDefinitionRepository.save(test1);

        LabTestDefinition test2 = LabTestDefinition.builder()
                .testCode("LIPID")
                .testName("Duplicate Lipid Panel")
                .category("Biochemistry")
                .specimenType("Plasma")
                .active(true)
                .createdAt(Instant.now())
                .build();

        assertThrows(DuplicateKeyException.class, () -> testDefinitionRepository.save(test2));
    }

    @Test
    @DisplayName("Mongo Integration: Lab order creation, specimen linking and status tracking")
    void labOrder_Specimen_Result_EndToEndPersistence() {
        // 1. Create order
        LabOrder order = LabOrder.builder()
                .orderNumber("ORD-INT-001")
                .patientId("patient-int-1")
                .doctorId("doc-int-1")
                .priority(OrderPriority.URGENT)
                .status(LabOrderStatus.ORDERED)
                .orderedTests(List.of(LabOrder.OrderedTestItem.builder()
                        .testCode("CBC")
                        .testName("Complete Blood Count")
                        .build()))
                .orderedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        LabOrder savedOrder = labOrderRepository.save(order);
        assertThat(savedOrder.getId()).isNotNull();

        // 2. Link specimen
        LabSpecimen specimen = LabSpecimen.builder()
                .specimenNumber("SPEC-INT-001")
                .labOrderId(savedOrder.getId())
                .patientId("patient-int-1")
                .specimenType("Blood")
                .collectionStatus(SpecimenStatus.COLLECTED)
                .collectedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        LabSpecimen savedSpecimen = labSpecimenRepository.save(specimen);
        assertThat(savedSpecimen.getId()).isNotNull();

        // 3. Enter result
        LabResult result = LabResult.builder()
                .labOrderId(savedOrder.getId())
                .specimenId(savedSpecimen.getId())
                .patientId("patient-int-1")
                .doctorId("doc-int-1")
                .testCode("CBC")
                .testName("Complete Blood Count")
                .resultType("NUMERIC")
                .numericValue(9.8)
                .unit("10^3/uL")
                .interpretation(LabResultInterpretation.NORMAL)
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .releasedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        LabResult savedResult = labResultRepository.save(result);
        assertThat(savedResult.getId()).isNotNull();

        // 4. Query released results for patient
        List<LabResult> patientResults = labResultRepository.findByPatientIdAndWorkflowStatus("patient-int-1", LabResultWorkflowStatus.RELEASED);
        assertThat(patientResults).hasSize(1);
        assertThat(patientResults.get(0).getTestCode()).isEqualTo("CBC");
    }

    @Test
    @DisplayName("Mongo Integration: Optimistic locking on LabResult prevents concurrent conflicts")
    void labResult_OptimisticLocking_PreventsConflictingUpdates() {
        LabResult result = LabResult.builder()
                .patientId("patient-int-1")
                .testCode("GLUC")
                .testName("Glucose")
                .numericValue(95.0)
                .workflowStatus(LabResultWorkflowStatus.ENTERED)
                .createdAt(Instant.now())
                .build();
        LabResult saved = labResultRepository.save(result);

        // Fetch two concurrent copies
        LabResult copy1 = labResultRepository.findById(saved.getId()).orElseThrow();
        LabResult copy2 = labResultRepository.findById(saved.getId()).orElseThrow();

        // Update copy1 first
        copy1.setNumericValue(98.0);
        labResultRepository.save(copy1);

        // Update copy2 with stale version
        copy2.setNumericValue(105.0);
        assertThrows(OptimisticLockingFailureException.class, () -> labResultRepository.save(copy2));
    }
}
