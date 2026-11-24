package com.hospital.backend.repository;

import com.hospital.backend.model.BillingCreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillingCreditNoteRepository extends MongoRepository<BillingCreditNote, String> {
    Optional<BillingCreditNote> findByCreditNoteNumber(String creditNoteNumber);
    List<BillingCreditNote> findByInvoiceId(String invoiceId);
    Page<BillingCreditNote> findByPatientId(String patientId, Pageable pageable);
}
