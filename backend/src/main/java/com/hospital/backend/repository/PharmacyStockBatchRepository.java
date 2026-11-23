package com.hospital.backend.repository;

import com.hospital.backend.enums.BatchStatus;
import com.hospital.backend.model.PharmacyStockBatch;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PharmacyStockBatchRepository extends MongoRepository<PharmacyStockBatch, String> {
    List<PharmacyStockBatch> findByMedicineId(String medicineId);
    List<PharmacyStockBatch> findByMedicineIdAndStatus(String medicineId, BatchStatus status);
    List<PharmacyStockBatch> findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(
            String medicineId, BatchStatus status, Instant expiryDate);
    List<PharmacyStockBatch> findByStatusAndExpiryDateBefore(BatchStatus status, Instant date);
    List<PharmacyStockBatch> findByStatusAndExpiryDateBetween(BatchStatus status, Instant from, Instant to);
    Optional<PharmacyStockBatch> findByBatchNumber(String batchNumber);
    Optional<PharmacyStockBatch> findByMedicineIdAndBatchNumber(String medicineId, String batchNumber);
    Optional<PharmacyStockBatch> findByIdempotencyKey(String idempotencyKey);
}
