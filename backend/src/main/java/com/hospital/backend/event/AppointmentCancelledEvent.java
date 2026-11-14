package com.hospital.backend.event;

import com.hospital.backend.model.Appointment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Domain event dispatched whenever an appointment is cancelled.
 */
@Getter
public class AppointmentCancelledEvent extends ApplicationEvent {

    private final Appointment appointment;
    private final String reason;
    private final Instant eventTime;

    public AppointmentCancelledEvent(Object source, Appointment appointment, String reason) {
        super(source);
        this.appointment = appointment;
        this.reason = reason;
        this.eventTime = Instant.now();
    }
}
