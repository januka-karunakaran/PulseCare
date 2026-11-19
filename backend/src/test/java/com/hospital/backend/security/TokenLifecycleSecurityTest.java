package com.hospital.backend.security;

import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.RefreshTokenRepository;
import com.hospital.backend.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenLifecycleSecurityTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private JwtService jwtService;

    @BeforeEach
    public void setup() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtService, "activeProfile", "dev");
    }

    @Test
    public void rotateRefreshToken_ValidToken_RevokesOldAndIssuesNew() {
        String oldToken = "old-refresh-token-uuid";
        RefreshToken existing = RefreshToken.builder()
                .id("db-id-1")
                .userId("user-123")
                .token(RefreshTokenServiceImpl.hashToken(oldToken))
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.deleteByToken(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(oldToken);

        assertNotNull(rotated);
        assertNotEquals(oldToken, rotated.getToken());
        assertEquals("user-123", rotated.getUserId());
        verify(refreshTokenRepository).deleteByToken(anyString());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    public void rotateRefreshToken_ReplayOfRevokedToken_ThrowsBadRequestException() {
        String consumedToken = "already-consumed-token";
        when(refreshTokenRepository.deleteByToken(anyString())).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                refreshTokenService.rotateRefreshToken(consumedToken));
        assertTrue(ex.getMessage().contains("Invalid or revoked refresh token"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    public void rotateRefreshToken_ExpiredToken_ThrowsBadRequestExceptionAndCleansUp() {
        String expiredToken = "expired-token-uuid";
        RefreshToken expired = RefreshToken.builder()
                .id("db-id-2")
                .userId("user-456")
                .token(RefreshTokenServiceImpl.hashToken(expiredToken))
                .expiryDate(Instant.now().minusSeconds(100))
                .build();

        when(refreshTokenRepository.deleteByToken(anyString())).thenReturn(Optional.of(expired));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                refreshTokenService.rotateRefreshToken(expiredToken));
        assertTrue(ex.getMessage().contains("Refresh token expired"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    public void concurrentRefreshRequests_OnlyOneSucceeds_OthersFail() throws Exception {
        String token = "shared-raw-refresh-token";
        RefreshToken existing = RefreshToken.builder()
                .id("db-id-concurrency")
                .userId("user-conc")
                .token(RefreshTokenServiceImpl.hashToken(token))
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        AtomicBoolean consumed = new AtomicBoolean(false);
        when(refreshTokenRepository.deleteByToken(anyString())).thenAnswer(invocation -> {
            if (consumed.compareAndSet(false, true)) {
                return Optional.of(existing);
            }
            return Optional.empty();
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RefreshToken rotated = refreshTokenService.rotateRefreshToken(token);
                    if (rotated != null) {
                        successCount.incrementAndGet();
                    }
                } catch (BadRequestException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent request must succeed in rotating the refresh token");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent requests must fail with BadRequestException");
    }

    @Test
    public void createRefreshToken_StoresHashedTokenInDatabase() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId("new-db-id");
            return token;
        });

        RefreshToken returnedToken = refreshTokenService.createRefreshToken("user-secure-hash");

        assertNotNull(returnedToken);
        String rawToken = returnedToken.getToken();
        assertNotNull(rawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken savedInDb = captor.getValue();

        assertNotEquals(rawToken, savedInDb.getToken(), "Raw token must not be stored in database");
        assertEquals(64, savedInDb.getToken().length(), "Token in database must be 64-character SHA-256 hex string");
        assertEquals(RefreshTokenServiceImpl.hashToken(rawToken), savedInDb.getToken());
    }

    @Test
    public void sameSecondLogoutAndImmediateReLogin_NewTokenIsValidAndOldTokenIsInvalid() throws Exception {
        User user = User.builder()
                .id("user-same-second")
                .email("samesec@hospital.com")
                .fullName("Same Second")
                .role(Role.PATIENT)
                .build();

        // 1. User logs in, gets Token 1
        String token1 = jwtService.generateToken(user, "ROLE_PATIENT");
        assertTrue(jwtService.isTokenValid(token1, user));

        Thread.sleep(15);

        // 2. User logs out in the same second
        user.setTokenInvalidBefore(Instant.now());

        // 3. Token 1 must immediately be invalid
        assertFalse(jwtService.isTokenValid(token1, user), "Old token must be invalid after logout");

        Thread.sleep(15);

        // 4. User immediately logs in again in the same second, gets Token 2
        String token2 = jwtService.generateToken(user, "ROLE_PATIENT");

        // 5. Token 2 must be valid
        assertTrue(jwtService.isTokenValid(token2, user), "New token issued upon re-login must remain valid");

        // 6. Token 1 must remain invalid
        assertFalse(jwtService.isTokenValid(token1, user), "Old token must remain invalid");
    }

    @Test
    public void accessToken_IssuedBeforeLogoutTimestamp_IsInvalid() throws Exception {
        User user = User.builder()
                .id("user-1")
                .email("user1@hospital.com")
                .fullName("User One")
                .role(Role.PATIENT)
                .build();

        String token = jwtService.generateToken(user, "ROLE_PATIENT");
        assertTrue(jwtService.isTokenValid(token, user));

        // Simulate user logout 1 second later
        Thread.sleep(100);
        user.setTokenInvalidBefore(Instant.now().plusSeconds(1));

        // Token must now be rejected as revoked
        assertFalse(jwtService.isTokenValid(token, user));
    }

    @Test
    public void productionProfile_WithInsecureOrDefaultSecret_FailsStartup() {
        JwtService prodJwtService = new JwtService();
        ReflectionTestUtils.setField(prodJwtService, "secretKey", "change-me-to-a-secure-secret-key");
        ReflectionTestUtils.setField(prodJwtService, "activeProfile", "prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, prodJwtService::validateSecretKey);
        assertTrue(ex.getMessage().contains("In production profile, a unique and secure JWT_SECRET"));
    }

    @Test
    public void productionProfile_WithStrongSecret_SucceedsStartup() {
        JwtService prodJwtService = new JwtService();
        ReflectionTestUtils.setField(prodJwtService, "secretKey", "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0");
        ReflectionTestUtils.setField(prodJwtService, "activeProfile", "prod");

        assertDoesNotThrow(prodJwtService::validateSecretKey);
    }
}
