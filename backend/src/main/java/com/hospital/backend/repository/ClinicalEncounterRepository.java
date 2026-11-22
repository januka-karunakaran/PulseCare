package com.hospital.backend.repository;

import com.hospital.backend.model.ClinicalEncounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalEncounterRepository extends MongoRepository<ClinicalEncounter, String> {
    Optional<ClinicalEncounter> findByAppointmentId(String appointmentId);
    List<ClinicalEncounter> findByPatientIdOrderByVisitStartedAtDesc(String patientId);
    Page<ClinicalEncounter> findByPatientId(String patientId, Pageable pageable);
    List<ClinicalEncounter> findByDoctorIdOrderByVisitStartedAtDesc(String doctorId);
    Page<ClinicalEncounter> findByDoctorId(String doctorId, Pageable pageable);
    boolean existsByAppointmentId(String appointmentId);
}
