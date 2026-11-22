package com.hospital.backend.scheduler;

import com.hospital.backend.service.OutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic Outbox Dispatcher.
 * Automatically polls and dispatches pending and retry outbox notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxProcessor outboxProcessor;
    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${app.scheduling.outbox-poll-ms:5000}")
    public void processOutboxQueue() {
        try {
            int processed = outboxProcessor.processBatch(BATCH_SIZE);
            if (processed > 0) {
                log.debug("OutboxScheduler processed {} items", processed);
            }
        } catch (Exception ex) {
            log.error("Unexpected error in OutboxScheduler run: {}", ex.getMessage(), ex);
        }
    }
}
