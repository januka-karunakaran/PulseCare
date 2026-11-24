package com.hospital.backend.controller;

import com.hospital.backend.dto.billing.*;
import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.model.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingService billingService;
    private final AuthorizationService authz;

    // =========================================================================
    // 1. Service Catalog Endpoints
    // =========================================================================

    @PostMapping("/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingServiceCatalog> createService(@Valid @RequestBody CreateServiceRequest request) {
        BillingServiceCatalog created = billingService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingServiceCatalog> updateService(
            @PathVariable String id,
            @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(billingService.updateService(id, request));
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingServiceCatalog> deactivateService(@PathVariable String id) {
        return ResponseEntity.ok(billingService.deactivateService(id));
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'DOCTOR', 'PATIENT')")
    public ResponseEntity<BillingServiceCatalog> getServiceById(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getServiceById(id));
    }

    @GetMapping("/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'DOCTOR', 'PATIENT')")
    public ResponseEntity<Page<BillingServiceCatalog>> getAllServices(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("serviceName").ascending());
        return ResponseEntity.ok(billingService.getAllServices(pageRequest, query));
    }

    @GetMapping("/services/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'DOCTOR', 'PATIENT')")
    public ResponseEntity<List<BillingServiceCatalog>> getActiveServices() {
        return ResponseEntity.ok(billingService.getActiveServices());
    }

    // =========================================================================
    // 2. Billable Charge Endpoints
    // =========================================================================

    @PostMapping("/charges/manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingCharge> createManualCharge(@Valid @RequestBody CreateManualChargeRequest request) {
        BillingCharge charge = billingService.createManualCharge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(charge);
    }

    @GetMapping("/charges/uninvoiced/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF') or @authz.canAccessPatient(#patientId)")
    public ResponseEntity<List<BillingCharge>> getUninvoicedCharges(@PathVariable String patientId) {
        return ResponseEntity.ok(billingService.getUninvoicedChargesByPatient(patientId));
    }

    @GetMapping("/charges/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF') or @authz.canAccessPatient(#patientId)")
    public ResponseEntity<Page<BillingCharge>> getPatientCharges(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(billingService.getChargesByPatient(patientId, pageRequest));
    }

    // =========================================================================
    // 3. Invoice Endpoints
    // =========================================================================

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Invoice> createInvoiceDraft(@Valid @RequestBody CreateInvoiceDraftRequest request) {
        Invoice invoice = billingService.createInvoiceDraft(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Invoice> issueInvoice(@PathVariable String id) {
        return ResponseEntity.ok(billingService.issueInvoice(id));
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Invoice> voidInvoice(@PathVariable String id, @RequestParam(defaultValue = "Administrative Void") String reason) {
        return ResponseEntity.ok(billingService.voidInvoice(id, reason));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'DOCTOR', 'PATIENT')")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getInvoiceById(id));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Page<Invoice>> getAllInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(billingService.getAllInvoices(pageRequest, status));
    }

    @GetMapping("/invoices/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'PATIENT')")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable String id) {
        byte[] pdf = billingService.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    // =========================================================================
    // 4. Payment Endpoints
    // =========================================================================

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingPayment> recordPayment(@Valid @RequestBody RecordPaymentRequest request) {
        BillingPayment payment = billingService.recordPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'PATIENT')")
    public ResponseEntity<BillingPayment> getPaymentById(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getPaymentById(id));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'PATIENT')")
    public ResponseEntity<List<BillingPayment>> getPaymentsByInvoice(@PathVariable String invoiceId) {
        return ResponseEntity.ok(billingService.getPaymentsByInvoice(invoiceId));
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'PATIENT')")
    public ResponseEntity<byte[]> downloadReceiptPdf(@PathVariable String id) {
        byte[] pdf = billingService.generateReceiptPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    @GetMapping("/invoices/{invoiceId}/ledger")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<List<BillingLedgerEntry>> getLedgerEntries(@PathVariable String invoiceId) {
        return ResponseEntity.ok(billingService.getLedgerEntriesByInvoice(invoiceId));
    }

    // =========================================================================
    // 5. Refunds & Credit Notes
    // =========================================================================

    @PostMapping("/payments/{id}/refunds")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingRefund> processRefund(
            @PathVariable String id,
            @Valid @RequestBody ProcessRefundRequest request) {
        request.setPaymentId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.processRefund(request));
    }

    @PostMapping("/credit-notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingCreditNote> issueCreditNote(@Valid @RequestBody IssueCreditNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.issueCreditNote(request));
    }

    @GetMapping("/credit-notes/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF', 'PATIENT')")
    public ResponseEntity<byte[]> downloadCreditNotePdf(@PathVariable String id) {
        byte[] pdf = billingService.generateCreditNotePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"credit-note-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    @GetMapping("/invoices/{invoiceId}/refunds")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<List<BillingRefund>> getRefundsByInvoice(@PathVariable String invoiceId) {
        return ResponseEntity.ok(billingService.getRefundsByInvoice(invoiceId));
    }

    @GetMapping("/invoices/{invoiceId}/credit-notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<List<BillingCreditNote>> getCreditNotesByInvoice(@PathVariable String invoiceId) {
        return ResponseEntity.ok(billingService.getCreditNotesByInvoice(invoiceId));
    }

    // =========================================================================
    // 6. Patient Portal Endpoints
    // =========================================================================

    @GetMapping("/my-invoices")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Page<Invoice>> getMyInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User currentUser = authz.getCurrentUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(billingService.getPatientInvoices(currentUser.getId(), pageRequest));
    }

    @GetMapping("/my-payments")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<Page<BillingPayment>> getMyPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User currentUser = authz.getCurrentUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(billingService.getPatientPayments(currentUser.getId(), pageRequest));
    }

    // =========================================================================
    // 7. Reports & Reconciliation
    // =========================================================================

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingSummaryDTO> getBillingSummary() {
        return ResponseEntity.ok(billingService.getBillingSummary());
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<ReconciliationReportDTO> getReconciliationReport() {
        return ResponseEntity.ok(billingService.getReconciliationReport());
    }
}
