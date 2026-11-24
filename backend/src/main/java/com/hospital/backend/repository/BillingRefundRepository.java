package com.hospital.backend.repository;

import com.hospital.backend.model.BillingRefund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingRefundRepository extends MongoRepository<BillingRefund, String> {
    Optional<BillingRefund> findByRefundNumber(String refundNumber);
    List<BillingRefund> findByPaymentId(String paymentId);
    List<BillingRefund> findByInvoiceId(String invoiceId);
    Page<BillingRefund> findByPatientId(String patientId, Pageable pageable);
}
