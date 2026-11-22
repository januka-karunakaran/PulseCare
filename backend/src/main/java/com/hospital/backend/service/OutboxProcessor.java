package com.hospital.backend.service;

import com.hospital.backend.dto.NotificationResponse;
import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Notification;
import com.hospital.backend.model.NotificationOutbox;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.NotificationOutboxRepository;
import com.hospital.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private final AppointmentRepository appointmentRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final MailService mailService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private static final int LEASE_SECONDS = 30;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    public String getWorkerId() {
        return workerId;
    }

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
            // Guard against reminders for cancelled or rescheduled appointments
            if ("APPOINTMENT_REMINDER_DUE".equals(item.getEventType()) && item.getAggregateId() != null) {
                Optional<Appointment> apptOpt = appointmentRepository.findById(item.getAggregateId());
                if (apptOpt.isEmpty()) {
                    cancelOutboxItem(item, "APPOINTMENT_NOT_FOUND", "Appointment no longer exists");
                    return;
                }
                Appointment appt = apptOpt.get();
                if (!"CONFIRMED".equalsIgnoreCase(appt.getStatus()) && !"PENDING".equalsIgnoreCase(appt.getStatus())) {
                    cancelOutboxItem(item, "APPOINTMENT_INACTIVE", "Appointment status is " + appt.getStatus());
                    return;
                }
                if (item.getMetadata() != null) {
                    String metaDate = item.getMetadata().get("date");
                    if (metaDate != null && !metaDate.equals(appt.getAppointmentDate())) {
                        cancelOutboxItem(item, "APPOINTMENT_RESCHEDULED", "Appointment rescheduled from " + metaDate + " to " + appt.getAppointmentDate());
                        return;
                    }
                }
            }

            if (item.getChannel() == OutboxChannel.IN_APP) {
                deliverInAppNotification(item);
                updateDeliveredWithLease(item, now);
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
                updateDeliveredWithLease(item, now);
                log.info("Outbox item {} (EMAIL) delivered to {}", item.getId(), maskEmail(item.getRecipientEmail()));
            }
        } catch (Exception ex) {
            handleDeliveryFailure(item, ex);
        }
    }

    private void cancelOutboxItem(NotificationOutbox item, String errorCode, String reason) {
        Instant now = Instant.now();
        item.setStatus(OutboxStatus.CANCELLED);
        item.setLastErrorCode(errorCode);
        item.setLastErrorMessage(reason);
        item.setUpdatedAt(now);
        outboxRepository.save(item);
        log.info("Outbox reminder item {} cancelled: {}", item.getId(), reason);
    }

    private boolean updateDeliveredWithLease(NotificationOutbox item, Instant now) {
        String claimer = (item.getClaimedBy() != null && !item.getClaimedBy().isBlank()) ? item.getClaimedBy() : workerId;
        Query query = new Query(Criteria.where("_id").is(item.getId())
                .and("status").is(OutboxStatus.PROCESSING.name())
                .and("claimedBy").is(claimer));

        Update update = new Update()
                .set("status", OutboxStatus.DELIVERED.name())
                .set("deliveredAt", now)
                .set("updatedAt", now);

        long modified = mongoTemplate.updateFirst(query, update, NotificationOutbox.class).getModifiedCount();
        if (modified == 0) {
            log.warn("Lease lost or stale worker completion for item {}. Not marking DELIVERED.", item.getId());
            return false;
        }
        item.setStatus(OutboxStatus.DELIVERED);
        item.setDeliveredAt(now);
        item.setUpdatedAt(now);
        return true;
    }

    private void deliverInAppNotification(NotificationOutbox item) {
        Notification notification;
        Optional<Notification> existing = notificationRepository.findBySourceOutboxId(item.getId());
        if (existing.isPresent()) {
            notification = existing.get();
            log.info("Idempotent hit: Notification already persisted for outbox item {}", item.getId());
        } else {
            notification = Notification.builder()
                    .sourceOutboxId(item.getId())
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
            try {
                notification = notificationRepository.save(notification);
            } catch (DuplicateKeyException ex) {
                notification = notificationRepository.findBySourceOutboxId(item.getId())
                        .orElse(notification);
            }
        }

        // Convert to response DTO
        NotificationResponse response = NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .actionUrl(notification.getActionUrl())
                .build();

        // Broadcast to user's private queue
        try {
            if (item.getRecipientEmail() != null && !item.getRecipientEmail().isBlank()) {
                messagingTemplate.convertAndSendToUser(
                        item.getRecipientEmail(),
                        "/queue/notifications",
                        response
                );
            }
            if (item.getRecipientUserId() != null && !item.getRecipientUserId().equals(item.getRecipientEmail())) {
                messagingTemplate.convertAndSendToUser(
                        item.getRecipientUserId(),
                        "/queue/notifications",
                        response
                );
            }
        } catch (Exception ex) {
            log.warn("Live WebSocket dispatch failed for notification {}: {}", notification.getId(), ex.getMessage());
        }
    }

    private void deliverEmailNotification(NotificationOutbox item) throws Exception {
        String toEmail = item.getRecipientEmail();
        if (toEmail == null || !toEmail.contains("@")) {
            throw new IllegalArgumentException("Invalid email address: " + maskEmail(toEmail));
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
        item.setLastErrorMessage(sanitizeErrorMessage(ex.getMessage()));

        boolean isPermanent = ex instanceof IllegalArgumentException ||
                (ex.getMessage() != null && ex.getMessage().contains("Invalid email address"));

        int maxAttempts = item.getMaxAttempts() > 0 ? item.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;

        if (isPermanent) {
            item.setStatus(OutboxStatus.FAILED);
            item.setLastErrorCode("PERMANENT_VALIDATION_ERROR");
            log.error("Outbox item {} failed permanently: {}", item.getId(), sanitizeErrorMessage(ex.getMessage()));
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
                    item.getId(), item.getAttemptCount(), maxAttempts, backoffSeconds, sanitizeErrorMessage(ex.getMessage()));
        }

        outboxRepository.save(item);
    }

    private long calculateBackoffSeconds(int attemptCount) {
        // Exponential backoff: 2^attempt * 5 seconds + jitter (1..5s), capped at 3600s
        long exponential = (long) Math.pow(2, Math.min(attemptCount, 10)) * 5;
        long jitter = ThreadLocalRandom.current().nextInt(1, 5);
        return Math.min(exponential + jitter, 3600);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + email.substring(atIndex);
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String sanitizeErrorMessage(String msg) {
        if (msg == null) return "Unknown error";
        // Strip out any email addresses or sensitive passwords from error messages
        return msg.replaceAll("(?i)(password|secret|token)=[^\\s,]+", "$1=***");
    }
}
