package com.hospital.backend.event;

import com.hospital.backend.enums.AppointmentEventType;
import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Event Publisher for Appointment Lifecycle Events.
 * Persists delivery intents durably to the Notification Outbox with deterministic
 * deduplication keys, ensuring at-least-once delivery even across process crashes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public void publishAppointmentCreated(Appointment appointment) {
        publishEvent(appointment, AppointmentEventType.APPOINTMENT_CREATED, null, null);
        // Also dispatch local Spring ApplicationEvent for any registered in-memory listeners
        try {
            applicationEventPublisher.publishEvent(new AppointmentCreatedEvent(this, appointment));
        } catch (Exception ex) {
            log.warn("Local Spring application event dispatch error: {}", ex.getMessage());
        }
    }

    public void publishAppointmentConfirmed(Appointment appointment) {
        publishEvent(appointment, AppointmentEventType.APPOINTMENT_CONFIRMED, null, null);
    }

    public void publishAppointmentCancelled(Appointment appointment, String reason) {
        // First cancel any pending or retry reminder/notification outbox records for this appointment
        outboxRepository.cancelPendingForAggregate(appointment.getId(), Instant.now());

        publishEvent(appointment, AppointmentEventType.APPOINTMENT_CANCELLED, null, reason);

        try {
            applicationEventPublisher.publishEvent(new AppointmentCancelledEvent(this, appointment, reason));
        } catch (Exception ex) {
            log.warn("Local Spring application event dispatch error: {}", ex.getMessage());
        }
    }

    public void publishAppointmentRescheduled(Appointment appointment, String oldDate, String oldTime) {
        // Cancel obsolete reminders
        outboxRepository.cancelPendingForAggregate(appointment.getId(), Instant.now());

        Map<String, String> extra = new HashMap<>();
        extra.put("oldDate", oldDate != null ? oldDate : "");
        extra.put("oldTime", oldTime != null ? oldTime : "");
        publishEvent(appointment, AppointmentEventType.APPOINTMENT_RESCHEDULED, extra, null);
    }

    public void publishAppointmentStatusChanged(Appointment appointment, String previousStatus, String newStatus) {
        if (previousStatus != null && previousStatus.equalsIgnoreCase(newStatus)) {
            // Rule: Do not produce duplicate events for repeated status updates that make no real change
            return;
        }

        if ("CANCELLED".equalsIgnoreCase(newStatus)) {
            publishAppointmentCancelled(appointment, "Status changed to CANCELLED");
            return;
        }

        Map<String, String> extra = new HashMap<>();
        extra.put("previousStatus", previousStatus != null ? previousStatus : "");
        extra.put("newStatus", newStatus != null ? newStatus : "");
        publishEvent(appointment, AppointmentEventType.APPOINTMENT_STATUS_CHANGED, extra, null);
    }

    private void publishEvent(Appointment appt, AppointmentEventType eventType, Map<String, String> extraMeta, String reason) {
        String eventId = "evt-" + UUID.randomUUID();
        Instant now = Instant.now();

        String patientUserId = resolvePatientUserId(appt.getPatientId());
        String patientEmail = resolvePatientEmail(appt.getPatientId());
        String patientName = resolvePatientName(appt.getPatientId());

        String doctorUserId = resolveDoctorUserId(appt.getDoctorId());
        String doctorEmail = resolveDoctorEmail(appt.getDoctorId());
        String doctorName = resolveDoctorName(appt.getDoctorId());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("appointmentId", appt.getId());
        metadata.put("date", appt.getAppointmentDate());
        metadata.put("time", appt.getAppointmentTime() != null ? appt.getAppointmentTime() : "");
        metadata.put("patientName", patientName);
        metadata.put("doctorName", doctorName);
        metadata.put("status", appt.getStatus());
        if (reason != null) metadata.put("reason", reason);
        if (extraMeta != null) metadata.putAll(extraMeta);

        // 1. Patient In-App Outbox Record
        if (patientUserId != null) {
            String pInAppKey = eventType.name() + ":" + appt.getId() + ":" + patientUserId + ":IN_APP";
            saveOutboxItem(eventId, eventType, appt.getId(), patientUserId, patientEmail, "PATIENT",
                    OutboxChannel.IN_APP, pInAppKey, getPatientTitle(eventType), getPatientMessage(eventType, appt, doctorName),
                    "/patient/appointments", metadata, now);
        }

        // 2. Patient Email Outbox Record
        if (patientEmail != null && patientUserId != null) {
            String pEmailKey = eventType.name() + ":" + appt.getId() + ":" + patientUserId + ":EMAIL";
            saveOutboxItem(eventId, eventType, appt.getId(), patientUserId, patientEmail, "PATIENT",
                    OutboxChannel.EMAIL, pEmailKey, getPatientTitle(eventType), getPatientMessage(eventType, appt, doctorName),
                    "/patient/appointments", metadata, now);
        }

        // 3. Doctor In-App Outbox Record
        if (doctorUserId != null) {
            String dInAppKey = eventType.name() + ":" + appt.getId() + ":" + doctorUserId + ":IN_APP";
            saveOutboxItem(eventId, eventType, appt.getId(), doctorUserId, doctorEmail, "DOCTOR",
                    OutboxChannel.IN_APP, dInAppKey, getDoctorTitle(eventType), getDoctorMessage(eventType, appt, patientName),
                    "/doctor/appointments", metadata, now);
        }

        // 4. Doctor Email Outbox Record
        if (doctorEmail != null && doctorUserId != null) {
            String dEmailKey = eventType.name() + ":" + appt.getId() + ":" + doctorUserId + ":EMAIL";
            saveOutboxItem(eventId, eventType, appt.getId(), doctorUserId, doctorEmail, "DOCTOR",
                    OutboxChannel.EMAIL, dEmailKey, getDoctorTitle(eventType), getDoctorMessage(eventType, appt, patientName),
                    "/doctor/appointments", metadata, now);
        }

        log.info("Persisted durable outbox events for appointment {} type {}", appt.getId(), eventType);
    }

    private void saveOutboxItem(String eventId, AppointmentEventType eventType, String aggregateId,
                                String recipientUserId, String recipientEmail, String recipientRole,
                                OutboxChannel channel, String deduplicationKey, String title,
                                String message, String actionUrl, Map<String, String> metadata, Instant now) {
        if (outboxRepository.existsByDeduplicationKey(deduplicationKey)) {
            return;
        }

        NotificationOutbox outbox = NotificationOutbox.builder()
                .eventId(eventId)
                .eventType(eventType.name())
                .aggregateId(aggregateId)
                .recipientUserId(recipientUserId)
                .recipientEmail(recipientEmail)
                .recipientRole(recipientRole)
                .channel(channel)
                .payloadVersion(1)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(5)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deduplicationKey(deduplicationKey)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .metadata(metadata)
                .build();

        try {
            outboxRepository.save(outbox);
        } catch (Exception ex) {
            // Deduplication duplicate key conflict handled safely
            log.debug("Outbox deduplication suppressed item for key {}: {}", deduplicationKey, ex.getMessage());
        }
    }

    private String getPatientTitle(AppointmentEventType type) {
        return switch (type) {
            case APPOINTMENT_CREATED, APPOINTMENT_CONFIRMED -> "Appointment Confirmed";
            case APPOINTMENT_CANCELLED -> "Appointment Cancelled";
            case APPOINTMENT_RESCHEDULED -> "Appointment Rescheduled";
            case APPOINTMENT_REMINDER_DUE -> "Appointment Reminder";
            case APPOINTMENT_STATUS_CHANGED -> "Appointment Updated";
        };
    }

    private String getPatientMessage(AppointmentEventType type, Appointment appt, String doctorName) {
        return switch (type) {
            case APPOINTMENT_CREATED, APPOINTMENT_CONFIRMED ->
                    "Your appointment with Dr. " + doctorName + " is scheduled for " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime();
            case APPOINTMENT_CANCELLED ->
                    "Your appointment with Dr. " + doctorName + " on " + appt.getAppointmentDate() + " has been cancelled";
            case APPOINTMENT_RESCHEDULED ->
                    "Your appointment with Dr. " + doctorName + " has been rescheduled to " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime();
            case APPOINTMENT_REMINDER_DUE ->
                    "Reminder: Appointment tomorrow with Dr. " + doctorName + " at " + appt.getAppointmentTime();
            case APPOINTMENT_STATUS_CHANGED ->
                    "Your appointment status has changed to " + appt.getStatus();
        };
    }

    private String getDoctorTitle(AppointmentEventType type) {
        return switch (type) {
            case APPOINTMENT_CREATED, APPOINTMENT_CONFIRMED -> "New Appointment Scheduled";
            case APPOINTMENT_CANCELLED -> "Appointment Cancelled";
            case APPOINTMENT_RESCHEDULED -> "Appointment Rescheduled";
            case APPOINTMENT_REMINDER_DUE -> "Tomorrow's Schedule Reminder";
            case APPOINTMENT_STATUS_CHANGED -> "Appointment Status Changed";
        };
    }

    private String getDoctorMessage(AppointmentEventType type, Appointment appt, String patientName) {
        return switch (type) {
            case APPOINTMENT_CREATED, APPOINTMENT_CONFIRMED ->
                    "New appointment booked with " + patientName + " on " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime();
            case APPOINTMENT_CANCELLED ->
                    "Appointment with " + patientName + " on " + appt.getAppointmentDate() + " was cancelled";
            case APPOINTMENT_RESCHEDULED ->
                    "Appointment with " + patientName + " was rescheduled to " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime();
            case APPOINTMENT_REMINDER_DUE ->
                    "Reminder: Consultation with " + patientName + " scheduled for tomorrow at " + appt.getAppointmentTime();
            case APPOINTMENT_STATUS_CHANGED ->
                    "Appointment with " + patientName + " changed to status " + appt.getStatus();
        };
    }

    private String resolvePatientUserId(String patientId) {
        if (patientId == null) return null;
        if (userRepository.existsById(patientId)) return patientId;
        return patientRepository.findById(patientId).map(Patient::getUserId).orElse(patientId);
    }

    private String resolvePatientEmail(String patientId) {
        String userId = resolvePatientUserId(patientId);
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    private String resolvePatientName(String patientId) {
        String userId = resolvePatientUserId(patientId);
        return userRepository.findById(userId).map(User::getFullName)
                .orElseGet(() -> patientRepository.findById(patientId).map(Patient::getFullName).orElse("Patient"));
    }

    private String resolveDoctorUserId(String doctorId) {
        if (doctorId == null) return null;
        if (userRepository.existsById(doctorId)) return doctorId;
        return doctorRepository.findById(doctorId).map(Doctor::getUserId).orElse(doctorId);
    }

    private String resolveDoctorEmail(String doctorId) {
        String userId = resolveDoctorUserId(doctorId);
        return userRepository.findById(userId).map(User::getEmail)
                .orElseGet(() -> doctorRepository.findById(doctorId).map(Doctor::getEmail).orElse(null));
    }

    private String resolveDoctorName(String doctorId) {
        String userId = resolveDoctorUserId(doctorId);
        return userRepository.findById(userId).map(User::getFullName)
                .orElseGet(() -> doctorRepository.findById(doctorId).map(Doctor::getFullName).orElse("Doctor"));
    }
}
