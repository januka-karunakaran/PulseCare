package com.hospital.backend.security;

import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.repository.RefreshTokenRepository;
import com.hospital.backend.service.impl.RefreshTokenServiceImpl;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real MongoDB Integration & Concurrency Test for Refresh Tokens.
 * Uses an isolated, disposable Testcontainers container per test execution.
 * Fail-closed: Never connects or falls back to any shared, local, or production database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
public class RefreshTokenMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_test_" + UUID.randomUUID().toString().replace("-", "");

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
            // Fail closed: Do NOT fallback to localhost:27017 or any shared database.
            registry.add("spring.data.mongodb.uri", () -> "mongodb://fail-closed-docker-required:27017/" + ISOLATED_TEST_DB);
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        }
    }

    @BeforeAll
    public static void verifyDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable() || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED TEST ISOLATION: Docker is required for RefreshTokenMongoIntegrationTest "
                    + "to run safely inside an isolated Testcontainers disposable database. Local shared database fallback is strictly prohibited.");
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
    private MongoTemplate mongoTemplate;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl instance1;
    private RefreshTokenServiceImpl instance2;

    @BeforeEach
    public void setup() {
        if (mongoDBContainer == null || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED SAFETY GUARD: Disposable Testcontainers MongoDB instance is NOT running. "
                    + "Refusing to execute integration tests against unisolated databases.");
        }

        String dbName = mongoTemplate.getDb().getName();

        // FAIL-CLOSED SAFETY GUARD:
        // Refuse to execute destructive operations against non-disposable, shared, or production databases
        if (dbName == null
                || !dbName.startsWith("disposable_test_")
                || dbName.equalsIgnoreCase("hospital_management_db")
                || dbName.toLowerCase().contains("prod")) {
            throw new IllegalStateException("CRITICAL SAFETY GUARD VIOLATION: Integration tests must run against an "
                    + "isolated disposable test database starting with 'disposable_test_'. Current connected database is '" + dbName
                    + "'. Aborting destructive commands!");
        }

        // Verify MongoDB connectivity
        mongoTemplate.getDb().runCommand(new Document("ping", 1));

        // Safe cleanup affecting ONLY the disposable test database
        mongoTemplate.dropCollection(RefreshToken.class);
        mongoTemplate.indexOps(RefreshToken.class).ensureIndex(
                new Index().on("token", Sort.Direction.ASC).unique()
        );

        // Instantiate two completely distinct service instances sharing the same database
        instance1 = new RefreshTokenServiceImpl(mongoTemplate, refreshTokenRepository);
        instance2 = new RefreshTokenServiceImpl(mongoTemplate, refreshTokenRepository);
    }

    @AfterEach
    public void cleanup() {
        try {
            String dbName = mongoTemplate.getDb().getName();
            if (dbName != null && dbName.startsWith("disposable_test_")
                    && !dbName.equalsIgnoreCase("hospital_management_db")
                    && !dbName.toLowerCase().contains("prod")) {
                mongoTemplate.dropCollection(RefreshToken.class);
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    public void concurrentRefreshRequests_AcrossTwoInstances_SharingSameDatabase_ExactlyOneSucceeds() throws Exception {
        // 1. Create a refresh token via instance 1
        RefreshToken initialToken = instance1.createRefreshToken("user-shared-db");
        String rawToken = initialToken.getToken();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedFailureCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> unexpectedExceptions = new ConcurrentLinkedQueue<>();
        AtomicReference<RefreshToken> succeededToken = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    // Alternate requests across two distinct service instances simulating multi-node deployment
                    RefreshTokenServiceImpl service = (index % 2 == 0) ? instance1 : instance2;
                    RefreshToken rotated = service.rotateRefreshToken(rawToken);
                    if (rotated != null) {
                        successCount.incrementAndGet();
                        succeededToken.set(rotated);
                    }
                } catch (BadRequestException e) {
                    if (e.getMessage().contains("Invalid or revoked refresh token")) {
                        expectedFailureCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads simultaneously
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred during concurrent rotation: " + unexpectedExceptions);
        assertEquals(1, successCount.get(), "Database-enforced atomicity must ensure exactly one request succeeds");
        assertEquals(threadCount - 1, expectedFailureCount.get(), "All other concurrent requests must fail with expected BadRequestException");

        // The old token must no longer exist in the database (queried by token field, NOT document _id)
        String hashedOldToken = RefreshTokenServiceImpl.hashToken(rawToken);
        Query oldTokenQuery = Query.query(Criteria.where("token").is(hashedOldToken));
        assertNull(mongoTemplate.findOne(oldTokenQuery, RefreshToken.class),
                "Consumed old token must be completely removed from MongoDB and no longer usable");

        // The new token must be persisted correctly
        assertNotNull(succeededToken.get());
        String hashedNewToken = RefreshTokenServiceImpl.hashToken(succeededToken.get().getToken());
        Query newTokenQuery = Query.query(Criteria.where("token").is(hashedNewToken));
        RefreshToken savedNewToken = mongoTemplate.findOne(newTokenQuery, RefreshToken.class);
        assertNotNull(savedNewToken, "Newly issued replacement token must be persisted in MongoDB");
        assertEquals("user-shared-db", savedNewToken.getUserId());
    }

    @Test
    public void uniqueTokenIndex_PreventsDuplicateTokensInDatabase() {
        String duplicateToken = RefreshTokenServiceImpl.hashToken("unique-token-test");
        RefreshToken t1 = RefreshToken.builder()
                .userId("user-1")
                .token(duplicateToken)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();
        mongoTemplate.save(t1);

        RefreshToken t2 = RefreshToken.builder()
                .userId("user-2")
                .token(duplicateToken)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        assertThrows(DuplicateKeyException.class, () ->
                mongoTemplate.save(t2), "MongoDB unique index on 'token' must reject duplicate token hashes");
    }

    @Test
    public void replayAttack_AcrossTwoInstances_SecondInstanceFails() {
        RefreshToken token = instance1.createRefreshToken("user-replay-test");
        String rawToken = token.getToken();

        // Instance 1 consumes the token successfully
        RefreshToken rotated1 = instance1.rotateRefreshToken(rawToken);
        assertNotNull(rotated1);
        assertNotEquals(rawToken, rotated1.getToken());

        // Instance 2 attempts to consume the already-consumed token -> must fail
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                instance2.rotateRefreshToken(rawToken));
        assertTrue(ex.getMessage().contains("Invalid or revoked refresh token"));
    }

    @Test
    public void expiredToken_AcrossTwoInstances_RejectedAndPurged() {
        String expiredRaw = "expired-raw-token";
        String hashedExpired = RefreshTokenServiceImpl.hashToken(expiredRaw);

        RefreshToken expired = RefreshToken.builder()
                .userId("user-expired")
                .token(hashedExpired)
                .expiryDate(Instant.now().minusSeconds(120))
                .build();
        refreshTokenRepository.save(expired);

        // Instance 2 attempts to rotate the expired token
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                instance2.rotateRefreshToken(expiredRaw));
        assertTrue(ex.getMessage().contains("Refresh token expired"));

        // Must be purged from MongoDB (queried by token field)
        Query query = Query.query(Criteria.where("token").is(hashedExpired));
        assertNull(mongoTemplate.findOne(query, RefreshToken.class));
    }
}
