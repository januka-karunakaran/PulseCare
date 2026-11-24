package com.hospital.backend.repository;

import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByPatientId(String patientId);
    Page<Invoice> findByPatientId(String patientId, Pageable pageable);
    List<Invoice> findByStatus(InvoiceStatus status);
    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);
    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);
}
