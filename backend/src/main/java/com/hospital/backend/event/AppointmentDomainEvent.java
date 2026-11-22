package com.hospital.backend.event;

import com.hospital.backend.enums.AppointmentEventType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable Enterprise Domain Event for Appointment Lifecycle State Transitions.
 */
@Getter
@Builder
public class AppointmentDomainEvent {

    private final String eventId;
    private final String aggregateId; // Appointment ID
    private final AppointmentEventType eventType;
    private final int eventVersion;
    private final Instant occurredAt;
    private final String correlationId;

    private final String patientId;
    private final String doctorId;
    private final String appointmentDate;
    private final String appointmentTime;
    private final String status;
    private final String previousStatus;
    private final String reason;

    private final Map<String, String> metadata;
}
