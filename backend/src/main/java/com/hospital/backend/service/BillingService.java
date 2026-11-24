package com.hospital.backend.service;

import com.hospital.backend.dto.billing.*;
import com.hospital.backend.enums.ChargeSourceType;
import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface BillingService {

    // 1. Service Catalog Management
    BillingServiceCatalog createService(CreateServiceRequest request);
    BillingServiceCatalog updateService(String id, UpdateServiceRequest request);
    BillingServiceCatalog deactivateService(String id);
    BillingServiceCatalog getServiceById(String id);
    Page<BillingServiceCatalog> getAllServices(Pageable pageable, String query);
    List<BillingServiceCatalog> getActiveServices();

    // 2. Billable Charge Registry
    BillingCharge createManualCharge(CreateManualChargeRequest request);
    BillingCharge registerClinicalCharge(ChargeSourceType sourceType, String sourceId, String sourceLineId,
                                         String patientId, String serviceCode, BigDecimal quantity, String description);
    List<BillingCharge> getUninvoicedChargesByPatient(String patientId);
    Page<BillingCharge> getChargesByPatient(String patientId, Pageable pageable);

    // 3. Invoice Lifecycle
    Invoice createInvoiceDraft(CreateInvoiceDraftRequest request);
    Invoice issueInvoice(String invoiceId);
    Invoice voidInvoice(String invoiceId, String reason);
    Invoice getInvoiceById(String invoiceId);
    Page<Invoice> getAllInvoices(Pageable pageable, InvoiceStatus status);
    Page<Invoice> getPatientInvoices(String patientId, Pageable pageable);

    // 4. Payments & Financial Transactions
    BillingPayment recordPayment(RecordPaymentRequest request);
    BillingPayment confirmPayment(String paymentId);
    BillingPayment getPaymentById(String paymentId);
    List<BillingPayment> getPaymentsByInvoice(String invoiceId);
    Page<BillingPayment> getPatientPayments(String patientId, Pageable pageable);
    List<BillingLedgerEntry> getLedgerEntriesByInvoice(String invoiceId);

    // 5. Refunds & Credit Notes
    BillingRefund processRefund(ProcessRefundRequest request);
    BillingCreditNote issueCreditNote(IssueCreditNoteRequest request);
    List<BillingRefund> getRefundsByInvoice(String invoiceId);
    List<BillingCreditNote> getCreditNotesByInvoice(String invoiceId);

    // 6. Reports & Reconciliation
    BillingSummaryDTO getBillingSummary();
    ReconciliationReportDTO getReconciliationReport();

    // 7. Documents (PDF)
    byte[] generateInvoicePdf(String invoiceId);
    byte[] generateReceiptPdf(String paymentId);
    byte[] generateCreditNotePdf(String creditNoteId);
}
