package com.hospital.backend.repository;

import com.hospital.backend.model.BedTransferRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedTransferRecordRepository extends MongoRepository<BedTransferRecord, String> {
    List<BedTransferRecord> findByAdmissionIdOrderByTransferredAtDesc(String admissionId);
    List<BedTransferRecord> findByPatientIdOrderByTransferredAtDesc(String patientId);
    Optional<BedTransferRecord> findByIdempotencyKey(String idempotencyKey);
}
