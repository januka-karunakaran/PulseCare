package com.hospital.backend.service;

import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.NotificationOutbox;
import com.hospital.backend.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Enterprise Notification Outbox MongoDB Integration Test.
 * Tests atomic lease acquisition, lease recovery, concurrency contention, and deduplication
 * using an isolated Testcontainers MongoDB instance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
public class NotificationOutboxMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_outbox_" + UUID.randomUUID().toString().replace("-", "");

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
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        } else {
            registry.add("spring.data.mongodb.uri", () -> "mongodb://fail-closed-docker-required:27017/" + ISOLATED_TEST_DB);
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        }
    }

    @BeforeAll
    public static void verifyDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable() || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED TEST ISOLATION: Docker is required for NotificationOutboxMongoIntegrationTest.");
        }
    }

    @AfterAll
    public static void stopContainer() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            try {
                mongoDBContainer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanCollection() {
        mongoTemplate.remove(new Query(), NotificationOutbox.class);
    }

    @Test
    @DisplayName("Multiple concurrent workers claiming outbox items should never claim the same item twice")
    void testAtomicClaimUnderConcurrentContention() throws InterruptedException {
        int itemCount = 15;
        Instant now = Instant.now();

        for (int i = 0; i < itemCount; i++) {
            NotificationOutbox item = NotificationOutbox.builder()
                    .eventId("evt-" + i)
                    .eventType("APPOINTMENT_CONFIRMED")
                    .aggregateId("appt-" + i)
                    .recipientUserId("user-" + i)
                    .channel(OutboxChannel.IN_APP)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(now.minusSeconds(10))
                    .createdAt(now)
                    .updatedAt(now)
                    .deduplicationKey("test:concurrent:" + i)
                    .build();
            outboxRepository.save(item);
        }

        int workerCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(workerCount);

        Set<String> claimedIds = ConcurrentHashMap.newKeySet();
        AtomicInteger totalClaims = new AtomicInteger(0);

        for (int w = 0; w < workerCount; w++) {
            final String workerId = "worker-" + w;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (true) {
                        Optional<NotificationOutbox> claim = outboxRepository.claimNextEligible(workerId, 30, Instant.now());
                        if (claim.isEmpty()) {
                            break;
                        }
                        String id = claim.get().getId();
                        boolean wasUnique = claimedIds.add(id);
                        if (!wasUnique) {
                            throw new IllegalStateException("CRITICAL: Double claim detected on item ID: " + id);
                        }
                        totalClaims.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // unblock all workers
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(totalClaims.get()).isEqualTo(itemCount);
        assertThat(claimedIds).hasSize(itemCount);

        // Verify all documents in DB are in PROCESSING state
        List<NotificationOutbox> all = outboxRepository.findAll();
        assertThat(all).hasSize(itemCount);
        for (NotificationOutbox doc : all) {
            assertThat(doc.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
            assertThat(doc.getClaimedBy()).isNotNull();
            assertThat(doc.getClaimUntil()).isAfter(Instant.now());
        }
    }

    @Test
    @DisplayName("Stuck PROCESSING items with expired claimUntil lease should be recovered by another worker")
    void testLeaseRecoveryForStuckProcessingItem() {
        Instant now = Instant.now();
        Instant expiredLease = now.minusSeconds(60);

        NotificationOutbox stuckItem = NotificationOutbox.builder()
                .eventId("evt-stuck")
                .eventType("APPOINTMENT_REMINDER_DUE")
                .aggregateId("appt-stuck-1")
                .recipientUserId("user-stuck-1")
                .channel(OutboxChannel.EMAIL)
                .status(OutboxStatus.PROCESSING)
                .claimedBy("crashed-worker-old")
                .claimUntil(expiredLease)
                .attemptCount(1)
                .maxAttempts(5)
                .createdAt(now.minusSeconds(120))
                .updatedAt(now.minusSeconds(60))
                .deduplicationKey("test:stuck:1")
                .build();
        outboxRepository.save(stuckItem);

        // A new worker claims next eligible item
        Optional<NotificationOutbox> recoveredOpt = outboxRepository.claimNextEligible("new-worker-alive", 30, now);

        assertThat(recoveredOpt).isPresent();
        NotificationOutbox recovered = recoveredOpt.get();
        assertThat(recovered.getId()).isEqualTo(stuckItem.getId());
        assertThat(recovered.getClaimedBy()).isEqualTo("new-worker-alive");
        assertThat(recovered.getClaimUntil()).isAfter(now);
    }

    @Test
    @DisplayName("Cancelling pending items by aggregateId should cancel PENDING and RETRY items but preserve DELIVERED")
    void testCancelPendingByAggregateId() {
        Instant now = Instant.now();
        String appointmentId = "appt-cancel-target";

        NotificationOutbox pending = NotificationOutbox.builder()
                .aggregateId(appointmentId)
                .status(OutboxStatus.PENDING)
                .deduplicationKey("test:cancel:1")
                .build();

        NotificationOutbox retry = NotificationOutbox.builder()
                .aggregateId(appointmentId)
                .status(OutboxStatus.RETRY)
                .deduplicationKey("test:cancel:2")
                .build();

        NotificationOutbox delivered = NotificationOutbox.builder()
                .aggregateId(appointmentId)
                .status(OutboxStatus.DELIVERED)
                .deduplicationKey("test:cancel:3")
                .build();

        outboxRepository.saveAll(List.of(pending, retry, delivered));

        long cancelledCount = outboxRepository.cancelPendingByAggregateId(appointmentId);

        assertThat(cancelledCount).isEqualTo(2);

        NotificationOutbox pendingAfter = outboxRepository.findByDeduplicationKey("test:cancel:1").orElseThrow();
        NotificationOutbox retryAfter = outboxRepository.findByDeduplicationKey("test:cancel:2").orElseThrow();
        NotificationOutbox deliveredAfter = outboxRepository.findByDeduplicationKey("test:cancel:3").orElseThrow();

        assertThat(pendingAfter.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(retryAfter.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(deliveredAfter.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
    }

    @Test
    @DisplayName("Inserting document with duplicate deduplicationKey should fail uniqueness constraint")
    void testDeduplicationKeyEnforcesUniqueness() {
        NotificationOutbox item1 = NotificationOutbox.builder()
                .eventId("evt-uniq-1")
                .status(OutboxStatus.PENDING)
                .deduplicationKey("unique-key-12345")
                .build();

        outboxRepository.save(item1);

        NotificationOutbox item2 = NotificationOutbox.builder()
                .eventId("evt-uniq-2")
                .status(OutboxStatus.PENDING)
                .deduplicationKey("unique-key-12345")
                .build();

        assertThrows(DuplicateKeyException.class, () -> mongoTemplate.insert(item2));
    }
}
