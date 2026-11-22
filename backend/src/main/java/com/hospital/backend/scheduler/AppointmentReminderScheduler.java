package com.hospital.backend.scheduler;

import com.hospital.backend.enums.AppointmentEventType;
import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Doctor;
import com.hospital.backend.model.NotificationOutbox;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.DoctorRepository;
import com.hospital.backend.repository.NotificationOutboxRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Enterprise Automated Appointment Reminder Scheduler.
 * Evaluates upcoming appointments in the configured business timezone (e.g. Asia/Colombo),
 * converts local date/time to unambiguous UTC Instants, and enqueues durable 24-hour reminders
 * into the notification outbox with deterministic deduplication and restart resilience.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${app.scheduling.timezone:Asia/Colombo}")
    private String configuredTimezone;

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)
    );

    /**
     * Runs periodically to scan for appointments requiring 24-hour reminders.
     */
    @Scheduled(cron = "${app.scheduling.reminder-cron:0 */15 * * * ?}")
    public void processUpcomingAppointmentReminders() {
        ZoneId zoneId = getSafeZoneId();
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
        Instant nowUtc = nowZoned.toInstant();

        // 24-hour reminder window: appointments starting between now and next 26 hours
        // (allows catch-up after delayed restarts without missing appointments)
        LocalDate today = nowZoned.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);

        List<String> targetDates = List.of(
                today.format(DateTimeFormatter.ISO_DATE),
                tomorrow.format(DateTimeFormatter.ISO_DATE),
                dayAfterTomorrow.format(DateTimeFormatter.ISO_DATE)
        );

        Query query = new Query(Criteria.where("appointmentDate").in(targetDates)
                .and("status").in(List.of("CONFIRMED", "PENDING")));

        List<Appointment> candidateAppointments = mongoTemplate.find(query, Appointment.class);

        int remindersScheduled = 0;
        for (Appointment appt : candidateAppointments) {
            try {
                if (processAppointmentReminder(appt, nowUtc, zoneId)) {
                    remindersScheduled++;
                }
            } catch (Exception ex) {
                log.warn("Failed to evaluate reminder for appointment {}: {}", appt.getId(), ex.getMessage());
            }
        }

        if (remindersScheduled > 0) {
            log.info("Scheduled {} appointment reminders (business timezone: {})", remindersScheduled, zoneId);
        }
    }

    public boolean processAppointmentReminder(Appointment appt, Instant nowUtc, ZoneId zoneId) {
        // 1. Verify status is still active (not cancelled, completed or rejected)
        if (!"CONFIRMED".equalsIgnoreCase(appt.getStatus()) && !"PENDING".equalsIgnoreCase(appt.getStatus())) {
            return false;
        }

        // 2. Parse appointment start Instant in configured business timezone
        Instant appointmentStartUtc = parseAppointmentStartInstant(appt.getAppointmentDate(), appt.getAppointmentTime(), zoneId);
        if (appointmentStartUtc == null) {
            return false;
        }

        // 3. Rule: Never send reminder after appointment has already started
        if (appointmentStartUtc.isBefore(nowUtc)) {
            return false;
        }

        // 4. Rule: Reminder is eligible if appointment starts within the next 24 hours (+ small 2h lookahead)
        // (i.e. now >= start - 24 hours and now < start)
        Instant reminderThreshold = appointmentStartUtc.minusSeconds(24 * 3600);
        if (nowUtc.isBefore(reminderThreshold)) {
            // Not yet in the 24-hour window
            return false;
        }

        // 5. Resolve patient and doctor recipient identifiers
        String patientUserId = resolvePatientUserId(appt.getPatientId());
        String patientEmail = resolvePatientEmail(appt.getPatientId());
        String patientName = resolvePatientName(appt.getPatientId());

        String doctorUserId = resolveDoctorUserId(appt.getDoctorId());
        String doctorEmail = resolveDoctorEmail(appt.getDoctorId());
        String doctorName = resolveDoctorName(appt.getDoctorId());

        boolean enqueuedAny = false;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("appointmentId", appt.getId());
        metadata.put("date", appt.getAppointmentDate());
        metadata.put("time", appt.getAppointmentTime() != null ? appt.getAppointmentTime() : "");
        metadata.put("patientName", patientName);
        metadata.put("doctorName", doctorName);
        metadata.put("status", appt.getStatus());

        // Enqueue Patient In-App Reminder
        String patientInAppKey = "reminder:24h:" + appt.getId() + ":" + patientUserId + ":IN_APP";
        if (patientUserId != null && !outboxRepository.existsByDeduplicationKey(patientInAppKey)) {
            NotificationOutbox patientInApp = NotificationOutbox.builder()
                    .eventId("rem-" + UUID.randomUUID())
                    .eventType(AppointmentEventType.APPOINTMENT_REMINDER_DUE.name())
                    .aggregateId(appt.getId())
                    .recipientUserId(patientUserId)
                    .recipientEmail(patientEmail)
                    .recipientRole("PATIENT")
                    .channel(OutboxChannel.IN_APP)
                    .payloadVersion(1)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(nowUtc)
                    .createdAt(nowUtc)
                    .updatedAt(nowUtc)
                    .deduplicationKey(patientInAppKey)
                    .title("Upcoming Appointment Reminder")
                    .message("Reminder: You have an appointment with Dr. " + doctorName + " on " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime())
                    .actionUrl("/patient/appointments")
                    .metadata(metadata)
                    .build();
            outboxRepository.save(patientInApp);
            enqueuedAny = true;
        }

        // Enqueue Patient Email Reminder
        String patientEmailKey = "reminder:24h:" + appt.getId() + ":" + patientUserId + ":EMAIL";
        if (patientEmail != null && !outboxRepository.existsByDeduplicationKey(patientEmailKey)) {
            NotificationOutbox patientEmailOutbox = NotificationOutbox.builder()
                    .eventId("rem-" + UUID.randomUUID())
                    .eventType(AppointmentEventType.APPOINTMENT_REMINDER_DUE.name())
                    .aggregateId(appt.getId())
                    .recipientUserId(patientUserId)
                    .recipientEmail(patientEmail)
                    .recipientRole("PATIENT")
                    .channel(OutboxChannel.EMAIL)
                    .payloadVersion(1)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(nowUtc)
                    .createdAt(nowUtc)
                    .updatedAt(nowUtc)
                    .deduplicationKey(patientEmailKey)
                    .title("Upcoming Appointment Reminder")
                    .message("Reminder: Appointment tomorrow with Dr. " + doctorName)
                    .actionUrl("/patient/appointments")
                    .metadata(metadata)
                    .build();
            outboxRepository.save(patientEmailOutbox);
            enqueuedAny = true;
        }

        // Enqueue Doctor In-App Reminder
        String doctorInAppKey = "reminder:24h:" + appt.getId() + ":" + doctorUserId + ":IN_APP";
        if (doctorUserId != null && !outboxRepository.existsByDeduplicationKey(doctorInAppKey)) {
            NotificationOutbox doctorInApp = NotificationOutbox.builder()
                    .eventId("rem-" + UUID.randomUUID())
                    .eventType(AppointmentEventType.APPOINTMENT_REMINDER_DUE.name())
                    .aggregateId(appt.getId())
                    .recipientUserId(doctorUserId)
                    .recipientEmail(doctorEmail)
                    .recipientRole("DOCTOR")
                    .channel(OutboxChannel.IN_APP)
                    .payloadVersion(1)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(nowUtc)
                    .createdAt(nowUtc)
                    .updatedAt(nowUtc)
                    .deduplicationKey(doctorInAppKey)
                    .title("Tomorrow's Appointment Reminder")
                    .message("Reminder: Consultation scheduled with " + patientName + " on " + appt.getAppointmentDate() + " at " + appt.getAppointmentTime())
                    .actionUrl("/doctor/appointments")
                    .metadata(metadata)
                    .build();
            outboxRepository.save(doctorInApp);
            enqueuedAny = true;
        }

        return enqueuedAny;
    }

    public Instant parseAppointmentStartInstant(String dateStr, String timeStr, ZoneId zoneId) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr.trim());
            LocalTime time = LocalTime.of(9, 0); // default fallback if time string is empty

            if (timeStr != null && !timeStr.isBlank()) {
                boolean parsed = false;
                for (DateTimeFormatter dtf : TIME_FORMATTERS) {
                    try {
                        time = LocalTime.parse(timeStr.trim().toUpperCase(Locale.ENGLISH), dtf);
                        parsed = true;
                        break;
                    } catch (DateTimeParseException ignored) {
                    }
                }
                if (!parsed) {
                    try {
                        time = LocalTime.parse(timeStr.trim());
                    } catch (Exception ignored) {
                    }
                }
            }

            return ZonedDateTime.of(date, time, zoneId).toInstant();
        } catch (Exception ex) {
            log.warn("Unable to parse appointment datetime: date={}, time={}", dateStr, timeStr);
            return null;
        }
    }

    public ZoneId getSafeZoneId() {
        try {
            if (configuredTimezone != null && !configuredTimezone.isBlank()) {
                return ZoneId.of(configuredTimezone.trim());
            }
        } catch (Exception ex) {
            log.warn("Invalid timezone {}, falling back to Asia/Colombo", configuredTimezone);
        }
        return ZoneId.of("Asia/Colombo");
    }

    private String resolvePatientUserId(String patientId) {
        if (patientId == null) return null;
        if (userRepository.existsById(patientId)) return patientId;
        return patientRepository.findById(patientId)
                .map(Patient::getUserId)
                .orElse(patientId);
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
        return doctorRepository.findById(doctorId)
                .map(Doctor::getUserId)
                .orElse(doctorId);
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
