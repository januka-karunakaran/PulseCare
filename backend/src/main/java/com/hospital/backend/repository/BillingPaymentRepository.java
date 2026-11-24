package com.hospital.backend.repository;

import com.hospital.backend.enums.PaymentStatus;
import com.hospital.backend.model.BillingPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingPaymentRepository extends MongoRepository<BillingPayment, String> {
    Optional<BillingPayment> findByPaymentNumber(String paymentNumber);
    List<BillingPayment> findByInvoiceId(String invoiceId);
    List<BillingPayment> findByInvoiceIdAndStatus(String invoiceId, PaymentStatus status);
    Page<BillingPayment> findByPatientId(String patientId, Pageable pageable);
    Optional<BillingPayment> findByIdempotencyKey(String idempotencyKey);
    Optional<BillingPayment> findByExternalReference(String externalReference);
}
