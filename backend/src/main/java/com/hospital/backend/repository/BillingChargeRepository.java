package com.hospital.backend.repository;

import com.hospital.backend.enums.ChargeSourceType;
import com.hospital.backend.enums.ChargeStatus;
import com.hospital.backend.model.BillingCharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingChargeRepository extends MongoRepository<BillingCharge, String> {
    Optional<BillingCharge> findByChargeNumber(String chargeNumber);
    Optional<BillingCharge> findBySourceTypeAndSourceIdAndSourceLineId(ChargeSourceType sourceType, String sourceId, String sourceLineId);
    List<BillingCharge> findByPatientIdAndChargeStatus(String patientId, ChargeStatus status);
    List<BillingCharge> findByPatientId(String patientId);
    Page<BillingCharge> findByPatientId(String patientId, Pageable pageable);
    List<BillingCharge> findByInvoiceId(String invoiceId);
    Optional<BillingCharge> findByIdempotencyKey(String idempotencyKey);
}
