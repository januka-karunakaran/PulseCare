package com.hospital.backend.repository;

import com.hospital.backend.model.Doctor;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface DoctorRepository extends MongoRepository<Doctor, String> {
    List<Doctor> findBySpecialization(String specialization);
    Optional<Doctor> findByEmail(String email);
    Optional<Doctor> findByUserId(String userId);
    List<Doctor> findByUserIdIn(Collection<String> userIds);
}
