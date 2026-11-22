package com.hospital.backend.scheduler;

import com.hospital.backend.repository.PasswordResetTokenRepository;
import com.hospital.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Enterprise Scheduled Task: Purges expired security tokens nightly.
 * Fully idempotent, safe against concurrent runs, and avoids database-wide scans via indexed deletion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Runs nightly at 00:00:00 (or configured cron) to purge expired tokens.
     */
    @Scheduled(cron = "${app.scheduling.token-cleanup-cron:0 0 0 * * ?}")
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        log.info("Starting scheduled security token cleanup job at {}", now);
        try {
            long deletedRefresh = refreshTokenRepository.deleteByExpiryDateBefore(now);
            long deletedReset = passwordResetTokenRepository.deleteByExpiresAtBefore(now);
            log.info("Completed scheduled token cleanup: purged {} expired refresh tokens and {} expired password reset tokens",
                    deletedRefresh, deletedReset);
        } catch (Exception ex) {
            log.error("Error during scheduled token cleanup: {}", ex.getMessage(), ex);
        }
    }
}
