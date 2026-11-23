package com.hospital.backend.repository;

import com.hospital.backend.model.PharmacyMedicine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyMedicineRepository extends MongoRepository<PharmacyMedicine, String> {
    Optional<PharmacyMedicine> findByMedicineCode(String medicineCode);
    List<PharmacyMedicine> findByActiveTrue();
    Page<PharmacyMedicine> findByActiveTrue(Pageable pageable);
    List<PharmacyMedicine> findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase(String genericName, String brandName);
}
