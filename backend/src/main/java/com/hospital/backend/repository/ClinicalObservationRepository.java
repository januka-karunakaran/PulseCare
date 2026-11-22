package com.hospital.backend.repository;

import com.hospital.backend.model.ClinicalObservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalObservationRepository extends MongoRepository<ClinicalObservation, String> {
    List<ClinicalObservation> findByEncounterIdOrderByObservedAtDesc(String encounterId);
    List<ClinicalObservation> findByPatientIdOrderByObservedAtDesc(String patientId);
    Page<ClinicalObservation> findByPatientIdOrderByObservedAtDesc(String patientId, Pageable pageable);
}
