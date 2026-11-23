package com.hospital.backend.repository;

import com.hospital.backend.enums.SpecimenStatus;
import com.hospital.backend.model.LabSpecimen;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LabSpecimenRepository extends MongoRepository<LabSpecimen, String> {
    Optional<LabSpecimen> findBySpecimenNumber(String specimenNumber);
    List<LabSpecimen> findByLabOrderId(String labOrderId);
    List<LabSpecimen> findByPatientId(String patientId);
    List<LabSpecimen> findByCollectionStatus(SpecimenStatus status);
}
