package com.hospital.backend.repository;

import com.hospital.backend.model.PatientClinicalProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientClinicalProfileRepository extends MongoRepository<PatientClinicalProfile, String> {
    Optional<PatientClinicalProfile> findByPatientId(String patientId);
}
