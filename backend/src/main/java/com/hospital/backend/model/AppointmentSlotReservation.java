package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Dedicated database-enforced reservation document for doctor appointment slots.
 * The primary key id is formatted as: doctorId_appointmentDate_appointmentTime
 * to guarantee strict single-capacity slot uniqueness across distributed instances.
 */
@Document(collection = "appointment_slot_reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "slot_reservation_compound_idx", def = "{'doctorId': 1, 'appointmentDate': 1, 'appointmentTime': 1}", unique = true)
public class AppointmentSlotReservation {

    @Id
    private String id; // Format: canonicalDoctorId_appointmentDate_normalizedTime

    @Indexed
    private String doctorId;

    private String appointmentDate;
    private String appointmentTime;

    @Indexed
    private String appointmentId;

    private String patientId;

    @Builder.Default
    private String status = "RESERVED";

    @Builder.Default
    private Instant createdAt = Instant.now();
}
