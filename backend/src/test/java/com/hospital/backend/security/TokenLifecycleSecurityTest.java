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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                .token(oldToken)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByToken(oldToken)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(oldToken);

        assertNotNull(rotated);
        assertNotEquals(oldToken, rotated.getToken());
        assertEquals("user-123", rotated.getUserId());
        verify(refreshTokenRepository).delete(existing);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    public void rotateRefreshToken_ReplayOfRevokedToken_ThrowsBadRequestException() {
        String consumedToken = "already-consumed-token";
        when(refreshTokenRepository.findByToken(consumedToken)).thenReturn(Optional.empty());

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
                .token(expiredToken)
                .expiryDate(Instant.now().minusSeconds(100))
                .build();

        when(refreshTokenRepository.findByToken(expiredToken)).thenReturn(Optional.of(expired));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                refreshTokenService.rotateRefreshToken(expiredToken));
        assertTrue(ex.getMessage().contains("Refresh token expired"));
        verify(refreshTokenRepository).delete(expired);
        verify(refreshTokenRepository, never()).save(any());
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
