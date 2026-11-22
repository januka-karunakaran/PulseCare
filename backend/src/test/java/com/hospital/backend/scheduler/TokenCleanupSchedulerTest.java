package com.hospital.backend.scheduler;

import com.hospital.backend.repository.PasswordResetTokenRepository;
import com.hospital.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Test
    @DisplayName("Should successfully delete expired refresh and reset tokens")
    void testCleanupExpiredTokensSuccess() {
        when(refreshTokenRepository.deleteByExpiryDateBefore(any(Instant.class))).thenReturn(15L);
        when(passwordResetTokenRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(5L);

        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should operate idempotently when no tokens are expired")
    void testCleanupExpiredTokensZeroCount() {
        when(refreshTokenRepository.deleteByExpiryDateBefore(any(Instant.class))).thenReturn(0L);
        when(passwordResetTokenRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(0L);

        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should gracefully handle and log repository exceptions without crashing")
    void testCleanupExpiredTokensResilientOnException() {
        when(refreshTokenRepository.deleteByExpiryDateBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("Database connectivity failure"));

        // Should not throw exception
        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
    }
}
