package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "appt_doc_date_idx", def = "{'doctorId': 1, 'appointmentDate': 1}"),
    @CompoundIndex(name = "appt_patient_date_idx", def = "{'patientId': 1, 'appointmentDate': 1}"),
    @CompoundIndex(name = "appt_status_date_idx", def = "{'status': 1, 'appointmentDate': 1}")
})
public class Appointment {

    @Id
    private String id;

    private String patientId;
    private String doctorId;

    private String appointmentDate; // example: 2026-04-20
    private String appointmentTime; // example: 10:30 AM
    private Integer tokenNumber; // Daily queue number for this doctor

    private String reason;
    private String status; // PENDING / APPROVED / REJECTED / COMPLETED
}
