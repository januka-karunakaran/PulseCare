package com.hospital.backend.security;

import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.repository.RefreshTokenRepository;
import com.hospital.backend.service.impl.RefreshTokenServiceImpl;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RefreshTokenMongoIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl instance1;
    private RefreshTokenServiceImpl instance2;

    @BeforeEach
    public void setup() {
        try {
            // Verify real MongoDB connectivity
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MongoDB is unavailable at runtime: " + e.getMessage());
        }

        // Clean collection and ensure unique token index
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
            mongoTemplate.dropCollection(RefreshToken.class);
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
        AtomicInteger failureCount = new AtomicInteger(0);

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
                    }
                } catch (BadRequestException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads simultaneously
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one concurrent request across separate instances must succeed
        assertEquals(1, successCount.get(), "Database-enforced atomicity must ensure exactly one request succeeds");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent requests must fail");

        // The old token must no longer exist in the database
        String hashedOldToken = RefreshTokenServiceImpl.hashToken(rawToken);
        assertNull(mongoTemplate.findById(hashedOldToken, RefreshToken.class));
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

        // Must be purged from MongoDB
        assertTrue(refreshTokenRepository.findByToken(hashedExpired).isEmpty());
    }
}
