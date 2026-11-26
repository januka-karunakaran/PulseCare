package com.hospital.backend.repository;

import com.hospital.backend.model.Bed;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedRepository extends MongoRepository<Bed, String> {
    List<Bed> findByStatus(String status);
    List<Bed> findByType(String type);
    List<Bed> findByWard(String ward);
    List<Bed> findByWardId(String wardId);
    List<Bed> findByStatusAndWardId(String status, String wardId);
    List<Bed> findByActive(boolean active);
    Optional<Bed> findByBedNumberAndWard(String bedNumber, String ward);
    Optional<Bed> findByBedNumberAndWardId(String bedNumber, String wardId);
    long countByStatus(String status);
    long countByActive(boolean active);
}
