package com.hospital.backend.repository;

import com.hospital.backend.enums.ServiceCategory;
import com.hospital.backend.model.BillingServiceCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingServiceCatalogRepository extends MongoRepository<BillingServiceCatalog, String> {
    Optional<BillingServiceCatalog> findByServiceCode(String serviceCode);
    List<BillingServiceCatalog> findByActiveTrue();
    Page<BillingServiceCatalog> findByActiveTrue(Pageable pageable);
    List<BillingServiceCatalog> findByCategoryAndActiveTrue(ServiceCategory category);
    Page<BillingServiceCatalog> findByServiceNameContainingIgnoreCaseOrServiceCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}
