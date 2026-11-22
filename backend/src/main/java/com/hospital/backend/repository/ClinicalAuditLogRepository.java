package com.hospital.backend.repository;

import com.hospital.backend.model.ClinicalAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalAuditLogRepository extends MongoRepository<ClinicalAuditLog, String> {
    List<ClinicalAuditLog> findTop100ByOrderByOccurredAtDesc();
    Page<ClinicalAuditLog> findByPatientIdOrderByOccurredAtDesc(String patientId, Pageable pageable);
    List<ClinicalAuditLog> findByResourceIdOrderByOccurredAtDesc(String resourceId);
}
