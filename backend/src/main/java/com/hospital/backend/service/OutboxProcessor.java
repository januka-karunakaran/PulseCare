package com.hospital.backend.service;

import com.hospital.backend.dto.NotificationResponse;
import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.Notification;
import com.hospital.backend.model.NotificationOutbox;
import com.hospital.backend.repository.NotificationOutboxRepository;
import com.hospital.backend.repository.NotificationRepository;
import com.hospital.backend.service.impl.MailServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enterprise Outbox Processor: Dispatches queued notifications with at-least-once guarantee,
 * atomic leases, exponential backoff, and idempotency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MailServiceImpl mailService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private static final int LEASE_SECONDS = 30;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    /**
     * Claims and processes a batch of eligible outbox entries.
     *
     * @param maxItems maximum items to process in this cycle
     * @return count of successfully processed entries
     */
    public int processBatch(int maxItems) {
        int processedCount = 0;
        Instant now = Instant.now();

        for (int i = 0; i < maxItems; i++) {
            Optional<NotificationOutbox> claimedOpt = outboxRepository.claimNextEligible(workerId, LEASE_SECONDS, now);
            if (claimedOpt.isEmpty()) {
                break;
            }

            NotificationOutbox item = claimedOpt.get();
            processItem(item);
            processedCount++;
        }

        return processedCount;
    }

    public void processItem(NotificationOutbox item) {
        Instant now = Instant.now();
        try {
            if (item.getChannel() == OutboxChannel.IN_APP) {
                deliverInAppNotification(item);
                item.setStatus(OutboxStatus.DELIVERED);
                item.setDeliveredAt(now);
                item.setUpdatedAt(now);
                outboxRepository.save(item);
                log.info("Outbox item {} (IN_APP) delivered to user {}", item.getId(), item.getRecipientUserId());
            } else if (item.getChannel() == OutboxChannel.EMAIL) {
                if (!mailEnabled) {
                    // Fail closed or mark disabled without falsely claiming external SMTP success
                    item.setStatus(OutboxStatus.CANCELLED);
                    item.setLastErrorCode("SMTP_DISABLED");
                    item.setLastErrorMessage("Mail service is disabled by configuration");
                    item.setUpdatedAt(now);
                    outboxRepository.save(item);
                    log.debug("Outbox item {} (EMAIL) skipped: SMTP disabled", item.getId());
                    return;
                }

                deliverEmailNotification(item);
                item.setStatus(OutboxStatus.DELIVERED);
                item.setDeliveredAt(now);
                item.setUpdatedAt(now);
                outboxRepository.save(item);
                log.info("Outbox item {} (EMAIL) delivered to {}", item.getId(), item.getRecipientEmail());
            }
        } catch (Exception ex) {
            handleDeliveryFailure(item, ex);
        }
    }

    private void deliverInAppNotification(NotificationOutbox item) {
        // Create in-app persistent notification entity
        Notification notification = Notification.builder()
                .userId(item.getRecipientUserId())
                .title(item.getTitle())
                .message(item.getMessage())
                .type(item.getEventType())
                .referenceId(item.getAggregateId())
                .referenceType(item.getEventType())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .actionUrl(item.getActionUrl())
                .build();

        Notification saved = notificationRepository.save(notification);

        // Convert to response DTO
        NotificationResponse response = NotificationResponse.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .title(saved.getTitle())
                .message(saved.getMessage())
                .type(saved.getType())
                .referenceId(saved.getReferenceId())
                .referenceType(saved.getReferenceType())
                .isRead(saved.isRead())
                .createdAt(saved.getCreatedAt())
                .actionUrl(saved.getActionUrl())
                .build();

        // Broadcast to user's private queue (both userId and email to guarantee delivery)
        try {
            messagingTemplate.convertAndSendToUser(
                    item.getRecipientUserId(),
                    "/queue/notifications",
                    response
            );
            if (item.getRecipientEmail() != null && !item.getRecipientEmail().equals(item.getRecipientUserId())) {
                messagingTemplate.convertAndSendToUser(
                        item.getRecipientEmail(),
                        "/queue/notifications",
                        response
                );
            }
        } catch (Exception ex) {
            log.warn("Live WebSocket dispatch failed for notification {}: {}", saved.getId(), ex.getMessage());
            // Live WebSocket failure does NOT fail the in-app delivery since it is persisted
        }
    }

    private void deliverEmailNotification(NotificationOutbox item) throws Exception {
        String toEmail = item.getRecipientEmail();
        if (toEmail == null || !toEmail.contains("@")) {
            throw new IllegalArgumentException("Invalid email address: " + toEmail);
        }

        String eventType = item.getEventType();
        String date = item.getMetadata() != null ? item.getMetadata().getOrDefault("date", "") : "";
        String time = item.getMetadata() != null ? item.getMetadata().getOrDefault("time", "") : "";
        String patientName = item.getMetadata() != null ? item.getMetadata().getOrDefault("patientName", "Patient") : "Patient";
        String doctorName = item.getMetadata() != null ? item.getMetadata().getOrDefault("doctorName", "Doctor") : "Doctor";

        boolean isPatient = "PATIENT".equalsIgnoreCase(item.getRecipientRole());

        switch (eventType) {
            case "APPOINTMENT_CREATED":
            case "APPOINTMENT_CONFIRMED":
                if (isPatient) {
                    mailService.sendAppointmentConfirmedToPatient(toEmail, patientName, doctorName, date, time);
                } else {
                    mailService.sendAppointmentConfirmedToDoctor(toEmail, doctorName, patientName, date, time);
                }
                break;
            case "APPOINTMENT_CANCELLED":
                if (isPatient) {
                    mailService.sendAppointmentCancelledToPatient(toEmail, patientName, doctorName, date, time);
                } else {
                    mailService.sendAppointmentCancelledToDoctor(toEmail, doctorName, patientName, date, time);
                }
                break;
            case "APPOINTMENT_REMINDER_DUE":
                if (isPatient) {
                    mailService.sendAppointmentReminderToPatient(toEmail, patientName, doctorName, date, time);
                } else {
                    mailService.sendAppointmentReminderToDoctor(toEmail, doctorName, patientName, date, time);
                }
                break;
            case "APPOINTMENT_RESCHEDULED":
            case "APPOINTMENT_STATUS_CHANGED":
                if (isPatient) {
                    String status = item.getMetadata() != null ? item.getMetadata().getOrDefault("status", "UPDATED") : "UPDATED";
                    mailService.sendAppointmentUpdatedToPatient(toEmail, patientName, doctorName, status);
                }
                break;
            default:
                log.debug("No dedicated email template for eventType: {}", eventType);
        }
    }

    private void handleDeliveryFailure(NotificationOutbox item, Exception ex) {
        Instant now = Instant.now();
        item.setAttemptCount(item.getAttemptCount() + 1);
        item.setUpdatedAt(now);
        item.setLastErrorMessage(ex.getMessage());

        boolean isPermanent = ex instanceof IllegalArgumentException ||
                (ex.getMessage() != null && ex.getMessage().contains("Invalid email address"));

        int maxAttempts = item.getMaxAttempts() > 0 ? item.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;

        if (isPermanent) {
            item.setStatus(OutboxStatus.FAILED);
            item.setLastErrorCode("PERMANENT_VALIDATION_ERROR");
            log.error("Outbox item {} failed permanently: {}", item.getId(), ex.getMessage());
        } else if (item.getAttemptCount() >= maxAttempts) {
            item.setStatus(OutboxStatus.FAILED);
            item.setLastErrorCode("MAX_ATTEMPTS_EXCEEDED");
            log.error("Outbox item {} reached max attempts ({}). Marked FAILED.", item.getId(), maxAttempts);
        } else {
            item.setStatus(OutboxStatus.RETRY);
            item.setLastErrorCode("TRANSIENT_DELIVERY_FAILURE");
            long backoffSeconds = calculateBackoffSeconds(item.getAttemptCount());
            item.setNextAttemptAt(now.plusSeconds(backoffSeconds));
            log.warn("Outbox item {} transient error. Attempt {}/{}. Retry in {}s: {}",
                    item.getId(), item.getAttemptCount(), maxAttempts, backoffSeconds, ex.getMessage());
        }

        outboxRepository.save(item);
    }

    private long calculateBackoffSeconds(int attemptCount) {
        // Exponential backoff: 2^attempt * 5 seconds + jitter (1..5s), capped at 3600s
        long exponential = (long) Math.pow(2, Math.min(attemptCount, 10)) * 5;
        long jitter = ThreadLocalRandom.current().nextInt(1, 5);
        return Math.min(exponential + jitter, 3600);
    }
}
