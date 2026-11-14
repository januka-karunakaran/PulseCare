package com.hospital.backend.event;

import com.hospital.backend.model.Appointment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Domain event dispatched whenever a new appointment is booked.
 */
@Getter
public class AppointmentCreatedEvent extends ApplicationEvent {

    private final Appointment appointment;
    private final Instant eventTime;

    public AppointmentCreatedEvent(Object source, Appointment appointment) {
        super(source);
        this.appointment = appointment;
        this.eventTime = Instant.now();
    }
}
