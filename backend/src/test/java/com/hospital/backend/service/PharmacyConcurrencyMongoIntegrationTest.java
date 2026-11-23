package com.hospital.backend.service;

import com.hospital.backend.enums.BatchStatus;
import com.hospital.backend.model.PharmacyStockBatch;
import com.hospital.backend.repository.PharmacyStockBatchRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
class PharmacyConcurrencyMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_pharm_" + UUID.randomUUID().toString().replace("-", "");

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
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (mongoDBContainer.isRunning()) {
            registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
        }
    }

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private PharmacyStockBatchRepository batchRepository;

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                mongoDBContainer.isRunning() && mongoTemplate != null,
                "Docker / MongoDB Testcontainer is not running; skipping integration test"
        );
        mongoTemplate.dropCollection(PharmacyStockBatch.class);
    }

    @Test
    @DisplayName("20 concurrent threads attempting to allocate stock prevents overselling (0 negative stock)")
    void testConcurrentStockDeduction_NoOverselling() throws InterruptedException {
        // Initial stock: 20 units
        PharmacyStockBatch batch = PharmacyStockBatch.builder()
                .medicineId("med-conc-1")
                .batchNumber("LOT-CONC-001")
                .quantityReceived(20.0)
                .quantityAvailable(20.0)
                .stockUnit("tablet")
                .status(BatchStatus.AVAILABLE)
                .expiryDate(Instant.now().plus(100, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        PharmacyStockBatch saved = batchRepository.save(batch);

        int numThreads = 20;
        double requestedPerThread = 2.0; // Total requested = 40.0 units, but only 20.0 are available!
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicInteger successfulAllocations = new AtomicInteger(0);
        AtomicInteger rejectedAllocations = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously

                    Query query = Query.query(
                            Criteria.where("id").is(saved.getId())
                                    .and("status").is(BatchStatus.AVAILABLE)
                                    .and("expiryDate").gt(Instant.now())
                                    .and("quantityAvailable").gte(requestedPerThread)
                    );
                    Update update = new Update()
                            .inc("quantityAvailable", -requestedPerThread)
                            .set("updatedAt", Instant.now());

                    UpdateResult result = mongoTemplate.updateFirst(query, update, PharmacyStockBatch.class);

                    if (result.getModifiedCount() > 0) {
                        successfulAllocations.incrementAndGet();
                    } else {
                        rejectedAllocations.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedAllocations.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads
        boolean finished = endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        // Exactly 10 requests should succeed (10 * 2.0 = 20.0 units)
        assertThat(successfulAllocations.get()).isEqualTo(10);
        // Exactly 10 requests should be rejected
        assertThat(rejectedAllocations.get()).isEqualTo(10);

        PharmacyStockBatch finalBatch = batchRepository.findById(saved.getId()).orElseThrow();
        // Remaining stock must be exactly 0.0, NEVER negative
        assertThat(finalBatch.getQuantityAvailable()).isEqualTo(0.0);
    }
}
