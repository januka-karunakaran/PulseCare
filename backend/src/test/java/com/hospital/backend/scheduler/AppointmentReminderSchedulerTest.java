package com.hospital.backend.scheduler;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderSchedulerTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AppointmentReminderScheduler reminderScheduler;

    private final ZoneId zoneId = ZoneId.of("Asia/Colombo");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reminderScheduler, "configuredTimezone", "Asia/Colombo");
    }

    @Test
    @DisplayName("Should correctly parse various appointment date and time formats into UTC Instant")
    void testParseAppointmentStartInstant() {
        Instant instant1 = reminderScheduler.parseAppointmentStartInstant("2026-12-05", "10:30 AM", zoneId);
        assertThat(instant1).isNotNull();

        Instant instant2 = reminderScheduler.parseAppointmentStartInstant("2026-12-05", "02:15 PM", zoneId);
        assertThat(instant2).isNotNull();

        Instant instant3 = reminderScheduler.parseAppointmentStartInstant("2026-12-05", "14:00", zoneId);
        assertThat(instant3).isNotNull();

        // 10:30 AM Colombo (UTC+5:30) is 05:00 UTC
        ZonedDateTime zdt1 = instant1.atZone(zoneId);
        assertThat(zdt1.getHour()).isEqualTo(10);
        assertThat(zdt1.getMinute()).isEqualTo(30);

        // Invalid format should return null rather than throw uncaught exception
        Instant invalid = reminderScheduler.parseAppointmentStartInstant("invalid-date", "10:00", zoneId);
        assertThat(invalid).isNull();
    }

    @Test
    @DisplayName("Should strictly reject missing or blank appointment time and not default to 09:00 AM")
    void testMissingAppointmentTimeReturnsNullWithoutDefaultingToNineAm() {
        Instant nullTime = reminderScheduler.parseAppointmentStartInstant("2026-12-05", null, zoneId);
        assertThat(nullTime).isNull();

        Instant blankTime = reminderScheduler.parseAppointmentStartInstant("2026-12-05", "   ", zoneId);
        assertThat(blankTime).isNull();

        Instant unparseableTime = reminderScheduler.parseAppointmentStartInstant("2026-12-05", "noon-ish", zoneId);
        assertThat(unparseableTime).isNull();
    }

    @Test
    @DisplayName("Should support deterministic Clock injection for time-dependent testing")
    void testClockInjectionForDeterministicEvaluation() {
        Instant fixedInstant = Instant.parse("2026-12-01T05:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, zoneId);
        reminderScheduler.setClock(fixedClock);

        // Appointment at 2026-12-02 10:30 AM Colombo is within 24 hours of fixed clock
        Appointment appt = Appointment.builder()
                .id("appt-clock-1")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate("2026-12-02")
                .appointmentTime("10:30 AM")
                .status("CONFIRMED")
                .build();

        when(userRepository.existsById("pat-1")).thenReturn(true);
        when(userRepository.existsById("doc-1")).thenReturn(true);
        when(outboxRepository.existsByDeduplicationKey(anyString())).thenReturn(false);

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, fixedInstant, zoneId);
        assertThat(enqueued).isTrue();
    }

    @Test
    @DisplayName("Should enqueue 24-hour reminder when appointment is within reminder window")
    void testProcessAppointmentReminderDue() {
        ZonedDateTime nowColombo = ZonedDateTime.now(zoneId);
        // Tomorrow at same hour -> within 24 hours
        ZonedDateTime apptTime = nowColombo.plusHours(20);

        Appointment appt = Appointment.builder()
                .id("appt-100")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate(apptTime.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .appointmentTime(apptTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
                .status("CONFIRMED")
                .build();

        User patientUser = User.builder().id("pat-user-1").email("patient@example.com").fullName("Patient One").build();
        User doctorUser = User.builder().id("doc-user-1").email("doctor@example.com").fullName("Dr. Doctor").build();

        when(userRepository.existsById("pat-1")).thenReturn(false);
        when(patientRepository.findById("pat-1")).thenReturn(Optional.of(Patient.builder().id("pat-1").userId("pat-user-1").build()));
        when(userRepository.findById("pat-user-1")).thenReturn(Optional.of(patientUser));

        when(userRepository.existsById("doc-1")).thenReturn(false);
        when(doctorRepository.findById("doc-1")).thenReturn(Optional.of(Doctor.builder().id("doc-1").userId("doc-user-1").build()));
        when(userRepository.findById("doc-user-1")).thenReturn(Optional.of(doctorUser));

        when(outboxRepository.existsByDeduplicationKey(anyString())).thenReturn(false);

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, nowColombo.toInstant(), zoneId);

        assertThat(enqueued).isTrue();
        // Verifies Patient In-App, Patient Email, Doctor In-App saved
        verify(outboxRepository, times(3)).save(any(NotificationOutbox.class));
    }

    @Test
    @DisplayName("Should suppress reminder if appointment is more than 26 hours away")
    void testAppointmentFarInFutureNotEnqueued() {
        ZonedDateTime nowColombo = ZonedDateTime.now(zoneId);
        ZonedDateTime apptTime = nowColombo.plusHours(48); // 2 days away

        Appointment appt = Appointment.builder()
                .id("appt-200")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate(apptTime.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .appointmentTime(apptTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
                .status("CONFIRMED")
                .build();

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, nowColombo.toInstant(), zoneId);

        assertThat(enqueued).isFalse();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should suppress reminder if appointment is in the past")
    void testPastAppointmentNotEnqueued() {
        ZonedDateTime nowColombo = ZonedDateTime.now(zoneId);
        ZonedDateTime apptTime = nowColombo.minusHours(2); // 2 hours ago

        Appointment appt = Appointment.builder()
                .id("appt-300")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate(apptTime.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .appointmentTime(apptTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
                .status("CONFIRMED")
                .build();

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, nowColombo.toInstant(), zoneId);

        assertThat(enqueued).isFalse();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should suppress reminder if appointment is cancelled or completed")
    void testCancelledAppointmentNotEnqueued() {
        ZonedDateTime nowColombo = ZonedDateTime.now(zoneId);
        ZonedDateTime apptTime = nowColombo.plusHours(12);

        Appointment appt = Appointment.builder()
                .id("appt-400")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate(apptTime.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .appointmentTime(apptTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
                .status("CANCELLED")
                .build();

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, nowColombo.toInstant(), zoneId);

        assertThat(enqueued).isFalse();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should suppress duplicate reminder if deduplicationKey already exists")
    void testDeduplicationSuppressesDuplicateReminder() {
        ZonedDateTime nowColombo = ZonedDateTime.now(zoneId);
        ZonedDateTime apptTime = nowColombo.plusHours(10);

        Appointment appt = Appointment.builder()
                .id("appt-500")
                .patientId("pat-1")
                .doctorId("doc-1")
                .appointmentDate(apptTime.toLocalDate().format(DateTimeFormatter.ISO_DATE))
                .appointmentTime(apptTime.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")))
                .status("CONFIRMED")
                .build();

        when(userRepository.existsById("pat-1")).thenReturn(true);
        when(userRepository.existsById("doc-1")).thenReturn(true);

        // Simulate deduplication keys already existing
        when(outboxRepository.existsByDeduplicationKey(anyString())).thenReturn(true);

        boolean enqueued = reminderScheduler.processAppointmentReminder(appt, nowColombo.toInstant(), zoneId);

        assertThat(enqueued).isFalse();
        verify(outboxRepository, never()).save(any());
    }
}
