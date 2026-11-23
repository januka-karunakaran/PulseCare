package com.hospital.backend.repository;

import com.hospital.backend.model.LabTestDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LabTestDefinitionRepository extends MongoRepository<LabTestDefinition, String> {
    Optional<LabTestDefinition> findByTestCode(String testCode);
    boolean existsByTestCode(String testCode);
    List<LabTestDefinition> findByActiveTrue();
    Page<LabTestDefinition> findByActive(boolean active, Pageable pageable);
    Page<LabTestDefinition> findByTestNameContainingIgnoreCaseOrTestCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}
