package com.hospital.backend.service;

import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Notification;
import com.hospital.backend.model.NotificationOutbox;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.NotificationOutboxRepository;
import com.hospital.backend.repository.NotificationRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MailService mailService;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxProcessor, "mailEnabled", true);
    }

    @Test
    @DisplayName("Should process batch and successfully deliver IN_APP notification")
    void testProcessBatchInAppSuccess() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-1")
                .eventId("evt-1")
                .eventType("APPOINTMENT_CONFIRMED")
                .aggregateId("appt-1")
                .recipientUserId("user-patient-1")
                .recipientEmail("patient@example.com")
                .recipientRole("PATIENT")
                .channel(OutboxChannel.IN_APP)
                .status(OutboxStatus.PROCESSING)
                .attemptCount(0)
                .maxAttempts(5)
                .title("Appointment Confirmed")
                .message("Your appointment is confirmed")
                .actionUrl("/patient/appointments")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(outboxRepository.claimNextEligible(anyString(), anyInt(), any(Instant.class)))
                .thenReturn(Optional.of(item))
                .thenReturn(Optional.empty());

        when(notificationRepository.findBySourceOutboxId("outbox-1")).thenReturn(Optional.empty());

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .sourceOutboxId("outbox-1")
                .userId("user-patient-1")
                .title("Appointment Confirmed")
                .message("Your appointment is confirmed")
                .type("APPOINTMENT_CONFIRMED")
                .referenceId("appt-1")
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        int processed = outboxProcessor.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        assertThat(item.getDeliveredAt()).isNotNull();

        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(eq("patient@example.com"), eq("/queue/notifications"), any());
    }

    @Test
    @DisplayName("Should idempotently handle in-app notification if already persisted on retry without duplicate creation")
    void testDeliverInAppNotificationIdempotentWhenAlreadyPersisted() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-idempotent-1")
                .channel(OutboxChannel.IN_APP)
                .status(OutboxStatus.PROCESSING)
                .recipientUserId("user-pat-10")
                .recipientEmail("pat10@example.com")
                .title("Idempotent Test")
                .message("Message")
                .build();

        Notification alreadySaved = Notification.builder()
                .id("notif-existing-10")
                .sourceOutboxId("outbox-idempotent-1")
                .userId("user-pat-10")
                .isRead(true) // User already read this notification
                .createdAt(LocalDateTime.now().minusHours(1))
                .readAt(LocalDateTime.now().minusMinutes(30))
                .build();

        when(notificationRepository.findBySourceOutboxId("outbox-idempotent-1")).thenReturn(Optional.of(alreadySaved));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        outboxProcessor.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        // CRITICAL: save must NEVER be called to avoid duplicate notifications and resetting isRead
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should suppress and cancel reminder outbox item if appointment is cancelled in DB before dispatch")
    void testProcessItemSuppressesReminderIfAppointmentCancelledInDatabase() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-rem-1")
                .eventType("APPOINTMENT_REMINDER_DUE")
                .aggregateId("appt-cancelled-99")
                .channel(OutboxChannel.EMAIL)
                .status(OutboxStatus.PROCESSING)
                .recipientEmail("pat@example.com")
                .build();

        Appointment cancelledAppt = Appointment.builder()
                .id("appt-cancelled-99")
                .status("CANCELLED")
                .build();

        when(appointmentRepository.findById("appt-cancelled-99")).thenReturn(Optional.of(cancelledAppt));

        outboxProcessor.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(item.getLastErrorCode()).isEqualTo("APPOINTMENT_INACTIVE");
        verify(mailService, never()).sendAppointmentReminderToPatient(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should deliver EMAIL notification when mail is enabled")
    void testProcessEmailNotificationSuccess() throws Exception {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-2")
                .eventId("evt-2")
                .eventType("APPOINTMENT_CONFIRMED")
                .aggregateId("appt-2")
                .recipientUserId("user-patient-2")
                .recipientEmail("patient2@example.com")
                .recipientRole("PATIENT")
                .channel(OutboxChannel.EMAIL)
                .status(OutboxStatus.PROCESSING)
                .attemptCount(0)
                .maxAttempts(5)
                .metadata(Map.of(
                        "patientName", "John Doe",
                        "doctorName", "Dr. Smith",
                        "date", "2026-12-01",
                        "time", "10:00 AM"
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(outboxRepository.claimNextEligible(anyString(), anyInt(), any(Instant.class)))
                .thenReturn(Optional.of(item))
                .thenReturn(Optional.empty());
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationOutbox.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        int processed = outboxProcessor.processBatch(5);

        assertThat(processed).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        verify(mailService).sendAppointmentConfirmedToPatient(
                eq("patient2@example.com"), eq("John Doe"), eq("Dr. Smith"), eq("2026-12-01"), eq("10:00 AM")
        );
    }

    @Test
    @DisplayName("Should cancel EMAIL notification cleanly if mail is disabled")
    void testProcessEmailCancelledWhenMailDisabled() {
        ReflectionTestUtils.setField(outboxProcessor, "mailEnabled", false);

        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-3")
                .channel(OutboxChannel.EMAIL)
                .status(OutboxStatus.PROCESSING)
                .recipientEmail("patient3@example.com")
                .build();

        when(outboxRepository.claimNextEligible(anyString(), anyInt(), any(Instant.class)))
                .thenReturn(Optional.of(item))
                .thenReturn(Optional.empty());

        int processed = outboxProcessor.processBatch(5);

        assertThat(processed).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(OutboxStatus.CANCELLED);
        assertThat(item.getLastErrorCode()).isEqualTo("SMTP_DISABLED");
        verify(mailService, never()).sendAppointmentConfirmedToPatient(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should transition to RETRY with exponential backoff on transient failure")
    void testTransientFailureTransitionsToRetry() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-4")
                .channel(OutboxChannel.IN_APP)
                .status(OutboxStatus.PROCESSING)
                .recipientUserId("user-4")
                .attemptCount(1)
                .maxAttempts(5)
                .build();

        when(notificationRepository.findBySourceOutboxId("outbox-4")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("Transient DB timeout"));

        outboxProcessor.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.RETRY);
        assertThat(item.getAttemptCount()).isEqualTo(2);
        assertThat(item.getLastErrorCode()).isEqualTo("TRANSIENT_DELIVERY_FAILURE");
        assertThat(item.getNextAttemptAt()).isAfter(Instant.now());
        verify(outboxRepository).save(item);
    }

    @Test
    @DisplayName("Should transition to FAILED when max attempts are exceeded")
    void testMaxAttemptsTransitionsToFailed() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-5")
                .channel(OutboxChannel.IN_APP)
                .status(OutboxStatus.PROCESSING)
                .recipientUserId("user-5")
                .attemptCount(4)
                .maxAttempts(5)
                .build();

        when(notificationRepository.findBySourceOutboxId("outbox-5")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("Repeated database failure"));

        outboxProcessor.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(item.getAttemptCount()).isEqualTo(5);
        assertThat(item.getLastErrorCode()).isEqualTo("MAX_ATTEMPTS_EXCEEDED");
        verify(outboxRepository).save(item);
    }

    @Test
    @DisplayName("Should immediately transition to FAILED on permanent validation error")
    void testPermanentFailureMarksFailedImmediately() {
        NotificationOutbox item = NotificationOutbox.builder()
                .id("outbox-6")
                .channel(OutboxChannel.EMAIL)
                .status(OutboxStatus.PROCESSING)
                .recipientEmail("invalid-email-address") // No @ symbol
                .attemptCount(0)
                .maxAttempts(5)
                .build();

        outboxProcessor.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(item.getLastErrorCode()).isEqualTo("PERMANENT_VALIDATION_ERROR");
        verify(outboxRepository).save(item);
    }
}
