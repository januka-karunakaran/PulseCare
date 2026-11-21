package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "presc_doc_created_idx", def = "{'doctorId': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "presc_patient_created_idx", def = "{'patientId': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "presc_medicines_idx", def = "{'medicines': 1}")
})
public class Prescription {

    @Id
    private String id;

    private String appointmentId;
    private String doctorId;
    private String patientId;

    private List<String> medicines;
    private String diagnosis;
    private String notes;
    private String dosageInstructions;
    private String createdAt;
}