package com.hospital.backend.repository;

import com.hospital.backend.model.HospitalWard;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalWardRepository extends MongoRepository<HospitalWard, String> {
    Optional<HospitalWard> findByWardCode(String wardCode);
    boolean existsByWardCode(String wardCode);
    List<HospitalWard> findByActive(boolean active);
}
