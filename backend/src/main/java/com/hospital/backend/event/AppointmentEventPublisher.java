package com.hospital.backend.event;

import com.hospital.backend.model.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Enterprise event publisher for appointment lifecycle events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishAppointmentCreated(Appointment appointment) {
        log.info("Publishing AppointmentCreatedEvent for appointmentId: {}", appointment.getId());
        eventPublisher.publishEvent(new AppointmentCreatedEvent(this, appointment));
    }

    public void publishAppointmentCancelled(Appointment appointment, String reason) {
        log.info("Publishing AppointmentCancelledEvent for appointmentId: {}, reason: {}", appointment.getId(), reason);
        eventPublisher.publishEvent(new AppointmentCancelledEvent(this, appointment, reason));
    }
}
