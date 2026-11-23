package com.hospital.backend.repository;

import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.model.LabOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LabOrderRepository extends MongoRepository<LabOrder, String> {
    Optional<LabOrder> findByOrderNumber(String orderNumber);
    List<LabOrder> findByPatientIdOrderByOrderedAtDesc(String patientId);
    List<LabOrder> findByDoctorIdOrderByOrderedAtDesc(String doctorId);
    List<LabOrder> findByEncounterId(String encounterId);
    List<LabOrder> findByStatus(LabOrderStatus status);
    Page<LabOrder> findByStatus(LabOrderStatus status, Pageable pageable);
    Page<LabOrder> findByPatientId(String patientId, Pageable pageable);
}
