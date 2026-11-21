package com.hospital.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "doctors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "doctor_spec_rating_idx", def = "{'specialization': 1, 'rating': -1}"),
    @CompoundIndex(name = "doctor_rating_idx", def = "{'rating': -1}")
})
public class Doctor {

    @Id
    private String id;

    private String userId;

    private String fullName;
    private String email;
    private String phone;
    private String specialization;
    private Integer experienceYears;
    private List<String> availableDays;
    private String status; // ACTIVE / INACTIVE
    private Double rating; // Average rating from patients (0-5)
    private Integer totalRatings; // Number of ratings received
}