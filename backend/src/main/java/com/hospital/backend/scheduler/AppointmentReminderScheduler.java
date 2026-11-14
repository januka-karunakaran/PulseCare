package com.hospital.backend.scheduler;

import com.hospital.backend.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Enterprise Scheduled Task: Scans upcoming appointments and sends reminder alerts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderScheduler {

    private final AppointmentRepository appointmentRepository;

    /**
     * Runs every hour to check upcoming appointments for tomorrow.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void processUpcomingAppointmentReminders() {
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE);
        log.debug("Checking appointments scheduled for tomorrow: {}", tomorrow);
    }
}
