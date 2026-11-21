package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Dedicated atomic counter for daily doctor queue token numbers.
 * The primary key id is formatted as: canonicalDoctorId_appointmentDate
 * to guarantee that concurrent appointment bookings across instances get
 * strictly unique and monotonically incrementing queue tokens.
 */
@Document(collection = "daily_queue_counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyQueueCounter {

    @Id
    private String id; // Format: canonicalDoctorId_appointmentDate

    private String doctorId;
    private String date;
    private Long seq;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
