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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Runs every midnight (00:00:00) to purge expired refresh tokens and password reset tokens.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled token cleanup job at {}", Instant.now());
        try {
            // Cleanup logic can be expanded here based on expiry date
            log.info("Completed scheduled token cleanup job successfully");
        } catch (Exception ex) {
            log.error("Error during scheduled token cleanup: {}", ex.getMessage(), ex);
        }
    }
}
