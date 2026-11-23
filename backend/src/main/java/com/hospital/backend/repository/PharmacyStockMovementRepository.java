package com.hospital.backend.repository;

import com.hospital.backend.model.PharmacyStockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyStockMovementRepository extends MongoRepository<PharmacyStockMovement, String> {
    List<PharmacyStockMovement> findByMedicineIdOrderByOccurredAtDesc(String medicineId);
    Page<PharmacyStockMovement> findByMedicineId(String medicineId, Pageable pageable);
    List<PharmacyStockMovement> findByBatchIdOrderByOccurredAtDesc(String batchId);
    List<PharmacyStockMovement> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
    Optional<PharmacyStockMovement> findByIdempotencyKey(String idempotencyKey);
}
