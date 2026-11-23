package com.hospital.backend.repository;

import com.hospital.backend.enums.DispenseStatus;
import com.hospital.backend.model.PharmacyDispense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyDispenseRepository extends MongoRepository<PharmacyDispense, String> {
    Optional<PharmacyDispense> findByDispenseNumber(String dispenseNumber);
    List<PharmacyDispense> findByPrescriptionIdOrderByCreatedAtDesc(String prescriptionId);
    List<PharmacyDispense> findByPatientIdOrderByDispensedAtDesc(String patientId);
    Page<PharmacyDispense> findByPatientId(String patientId, Pageable pageable);
    Optional<PharmacyDispense> findByIdempotencyKey(String idempotencyKey);
    List<PharmacyDispense> findByStatus(DispenseStatus status);
}
