package com.hospital.backend.repository;

import com.hospital.backend.model.BillingLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingLedgerEntryRepository extends MongoRepository<BillingLedgerEntry, String> {
    Optional<BillingLedgerEntry> findByEntryNumber(String entryNumber);
    List<BillingLedgerEntry> findByInvoiceIdOrderByOccurredAtAsc(String invoiceId);
    Page<BillingLedgerEntry> findByInvoiceId(String invoiceId, Pageable pageable);
    Page<BillingLedgerEntry> findByPatientId(String patientId, Pageable pageable);
    Optional<BillingLedgerEntry> findByIdempotencyKey(String idempotencyKey);
}
