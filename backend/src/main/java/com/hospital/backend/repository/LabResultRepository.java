package com.hospital.backend.repository;

import com.hospital.backend.enums.LabResultWorkflowStatus;
import com.hospital.backend.model.LabResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LabResultRepository extends MongoRepository<LabResult, String> {
    List<LabResult> findByPatientId(String patientId);
    List<LabResult> findByDoctorId(String doctorId);
    List<LabResult> findByAppointmentId(String appointmentId);
    List<LabResult> findByLabOrderId(String labOrderId);
    List<LabResult> findBySpecimenId(String specimenId);
    List<LabResult> findByEncounterId(String encounterId);
    List<LabResult> findByPatientIdAndWorkflowStatus(String patientId, LabResultWorkflowStatus workflowStatus);
    Optional<LabResult> findByLabOrderIdAndTestCode(String labOrderId, String testCode);
}
