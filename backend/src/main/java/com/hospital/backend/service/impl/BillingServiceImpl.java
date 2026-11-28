package com.hospital.backend.service.impl;

import com.hospital.backend.dto.billing.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.*;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.BillingPdfService;
import com.hospital.backend.service.BillingService;
import com.hospital.backend.service.ClinicalAuditService;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingServiceImpl implements BillingService {

    private final BillingServiceCatalogRepository catalogRepository;
    private final BillingChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingLedgerEntryRepository ledgerEntryRepository;
    private final BillingCreditNoteRepository creditNoteRepository;
    private final BillingRefundRepository refundRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthorizationService authz;
    private final ClinicalAuditService auditService;
    private final BillingPdfService billingPdfService;

    private static final String DEFAULT_CURRENCY = "LKR";
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // =========================================================================
    // 1. Service Catalog Management
    // =========================================================================

    @Override
    public BillingServiceCatalog createService(CreateServiceRequest request) {
        if (!authz.canManageBillingCatalog()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can manage service catalog");
        }

        String code = request.getServiceCode().trim().toUpperCase();
        if (catalogRepository.findByServiceCode(code).isPresent()) {
            throw new DuplicateResourceException("Service code already exists: " + code);
        }

        BigDecimal price = request.getUnitPrice().setScale(2, ROUNDING);

        BillingServiceCatalog service = BillingServiceCatalog.builder()
                .serviceCode(code)
                .serviceName(request.getServiceName().trim())
                .category(request.getCategory())
                .description(request.getDescription())
                .currency(request.getCurrency() != null ? request.getCurrency().trim().toUpperCase() : DEFAULT_CURRENCY)
                .unitPrice(price)
                .taxCategory(request.getTaxCategory() != null ? request.getTaxCategory().trim().toUpperCase() : "STANDARD")
                .active(true)
                .effectiveFrom(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BillingServiceCatalog saved = catalogRepository.save(service);
        auditService.audit(null, "BillingServiceCatalog", saved.getId(),
                ClinicalAuditAction.BILLING_SERVICE_CREATED, "SUCCESS", null,
                "Created service " + saved.getServiceCode() + " (" + saved.getServiceName() + ")");
        log.info("Created billing service: {} - {}", saved.getServiceCode(), saved.getServiceName());
        return saved;
    }

    @Override
    public BillingServiceCatalog updateService(String id, UpdateServiceRequest request) {
        if (!authz.canManageBillingCatalog()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can update service catalog");
        }

        BillingServiceCatalog service = catalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service definition not found: " + id));

        if (request.getServiceName() != null) service.setServiceName(request.getServiceName().trim());
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getUnitPrice() != null) service.setUnitPrice(request.getUnitPrice().setScale(2, ROUNDING));
        if (request.getTaxCategory() != null) service.setTaxCategory(request.getTaxCategory().trim().toUpperCase());
        if (request.getActive() != null) service.setActive(request.getActive());

        service.setUpdatedAt(Instant.now());
        BillingServiceCatalog saved = catalogRepository.save(service);
        auditService.audit(null, "BillingServiceCatalog", saved.getId(),
                ClinicalAuditAction.BILLING_SERVICE_UPDATED, "SUCCESS", null,
                "Updated service " + saved.getServiceCode());
        return saved;
    }

    @Override
    public BillingServiceCatalog deactivateService(String id) {
        if (!authz.canManageBillingCatalog()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can deactivate services");
        }

        BillingServiceCatalog service = catalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service definition not found: " + id));

        service.setActive(false);
        service.setEffectiveUntil(Instant.now());
        service.setUpdatedAt(Instant.now());
        return catalogRepository.save(service);
    }

    @Override
    public BillingServiceCatalog getServiceById(String id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service definition not found: " + id));
    }

    @Override
    public Page<BillingServiceCatalog> getAllServices(Pageable pageable, String query) {
        if (query != null && !query.trim().isEmpty()) {
            return catalogRepository.findByServiceNameContainingIgnoreCaseOrServiceCodeContainingIgnoreCase(
                    query.trim(), query.trim(), pageable);
        }
        return catalogRepository.findAll(pageable);
    }

    @Override
    public List<BillingServiceCatalog> getActiveServices() {
        return catalogRepository.findByActiveTrue();
    }

    // =========================================================================
    // 2. Billable Charge Registry
    // =========================================================================

    @Override
    public BillingCharge createManualCharge(CreateManualChargeRequest request) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can create manual charges");
        }

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<BillingCharge> existing = chargeRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        BillingServiceCatalog catalog = catalogRepository.findByServiceCode(request.getServiceCode().trim().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Service code not found: " + request.getServiceCode()));

        User currentUser = authz.getCurrentUser();
        BigDecimal qty = request.getQuantity().setScale(2, ROUNDING);
        BigDecimal price = request.getUnitPrice().setScale(2, ROUNDING);
        BigDecimal subtotal = qty.multiply(price).setScale(2, ROUNDING);
        BigDecimal tax = BigDecimal.ZERO.setScale(2, ROUNDING); // Default zero unless standard policy
        BigDecimal total = subtotal.add(tax).setScale(2, ROUNDING);

        String chargeNumber = "CHG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        BillingCharge charge = BillingCharge.builder()
                .chargeNumber(chargeNumber)
                .patientId(request.getPatientId())
                .sourceType(ChargeSourceType.MANUAL_SERVICE)
                .sourceId("MANUAL-" + UUID.randomUUID())
                .sourceLineId("LINE-1")
                .serviceCode(catalog.getServiceCode())
                .descriptionSnapshot(request.getDescription() != null ? request.getDescription() : catalog.getServiceName())
                .quantity(qty)
                .unitPrice(price)
                .lineSubtotal(subtotal)
                .taxSnapshot(tax)
                .totalAmount(total)
                .currency(catalog.getCurrency())
                .chargeStatus(ChargeStatus.PENDING)
                .createdByUserId(currentUser.getId())
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BillingCharge saved = chargeRepository.save(charge);
        auditService.audit(request.getPatientId(), "BillingCharge", saved.getId(),
                ClinicalAuditAction.BILLING_CHARGE_CREATED, "SUCCESS", null,
                "Manual charge created: " + saved.getChargeNumber() + " Reason: " + request.getReason());
        return saved;
    }

    @Override
    public BillingCharge registerClinicalCharge(ChargeSourceType sourceType, String sourceId, String sourceLineId,
                                                String patientId, String serviceCode, BigDecimal quantity, String description) {
        // Prevent duplicate charges for the exact same event
        Optional<BillingCharge> existing = chargeRepository.findBySourceTypeAndSourceIdAndSourceLineId(sourceType, sourceId, sourceLineId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BillingServiceCatalog catalog = catalogRepository.findByServiceCode(serviceCode)
                .orElseGet(() -> BillingServiceCatalog.builder()
                        .serviceCode(serviceCode)
                        .serviceName(description != null ? description : serviceCode)
                        .unitPrice(new BigDecimal("1000.00"))
                        .currency(DEFAULT_CURRENCY)
                        .build());

        BigDecimal qty = quantity != null ? quantity.setScale(2, ROUNDING) : BigDecimal.ONE.setScale(2, ROUNDING);
        BigDecimal price = catalog.getUnitPrice().setScale(2, ROUNDING);
        BigDecimal subtotal = qty.multiply(price).setScale(2, ROUNDING);
        BigDecimal tax = BigDecimal.ZERO.setScale(2, ROUNDING);
        BigDecimal total = subtotal.add(tax).setScale(2, ROUNDING);

        String chargeNumber = "CHG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        BillingCharge charge = BillingCharge.builder()
                .chargeNumber(chargeNumber)
                .patientId(patientId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .sourceLineId(sourceLineId)
                .serviceCode(serviceCode)
                .descriptionSnapshot(description != null ? description : catalog.getServiceName())
                .quantity(qty)
                .unitPrice(price)
                .lineSubtotal(subtotal)
                .taxSnapshot(tax)
                .totalAmount(total)
                .currency(catalog.getCurrency())
                .chargeStatus(ChargeStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return chargeRepository.save(charge);
    }

    @Override
    public List<BillingCharge> getUninvoicedChargesByPatient(String patientId) {
        return chargeRepository.findByPatientIdAndChargeStatus(patientId, ChargeStatus.PENDING);
    }

    @Override
    public Page<BillingCharge> getChargesByPatient(String patientId, Pageable pageable) {
        if (!authz.canAccessPatient(patientId) && !authz.canManageBilling()) {
            throw new AccessDeniedException("Not authorized to view patient charges");
        }
        return chargeRepository.findByPatientId(patientId, pageable);
    }

    // =========================================================================
    // 3. Invoice Generation & Lifecycle
    // =========================================================================

    @Override
    @Transactional
    public Invoice createInvoiceDraft(CreateInvoiceDraftRequest request) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can create invoices");
        }

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Invoice> existing = invoiceRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String patientId = request.getPatientId();
        String patientName = patientRepository.findById(patientId).map(Patient::getFullName)
                .orElseGet(() -> userRepository.findById(patientId).map(User::getFullName).orElse("Hospital Patient"));

        List<BillingCharge> charges = chargeRepository.findAllById(request.getChargeIds());
        if (charges.isEmpty()) {
            throw new BadRequestException("At least one valid charge is required to generate an invoice");
        }

        List<Invoice.InvoiceItem> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, ROUNDING);
        BigDecimal taxTotal = BigDecimal.ZERO.setScale(2, ROUNDING);

        for (BillingCharge charge : charges) {
            if (!charge.getPatientId().equals(patientId)) {
                throw new BadRequestException("Charge " + charge.getChargeNumber() + " belongs to a different patient");
            }
            if (charge.getChargeStatus() != ChargeStatus.PENDING) {
                throw new BadRequestException("Charge " + charge.getChargeNumber() + " is already in status: " + charge.getChargeStatus());
            }

            BigDecimal lineSub = charge.getLineSubtotal() != null ? charge.getLineSubtotal().setScale(2, ROUNDING) :
                    charge.getQuantity().multiply(charge.getUnitPrice()).setScale(2, ROUNDING);
            BigDecimal lineTax = charge.getTaxSnapshot() != null ? charge.getTaxSnapshot().setScale(2, ROUNDING) : BigDecimal.ZERO.setScale(2, ROUNDING);
            BigDecimal lineTotal = lineSub.add(lineTax).setScale(2, ROUNDING);

            subtotal = subtotal.add(lineSub).setScale(2, ROUNDING);
            taxTotal = taxTotal.add(lineTax).setScale(2, ROUNDING);

            lineItems.add(Invoice.InvoiceItem.builder()
                    .chargeId(charge.getId())
                    .serviceCode(charge.getServiceCode())
                    .description(charge.getDescriptionSnapshot())
                    .quantity(charge.getQuantity())
                    .unitPrice(charge.getUnitPrice())
                    .lineSubtotal(lineSub)
                    .discountAmount(BigDecimal.ZERO.setScale(2, ROUNDING))
                    .taxAmount(lineTax)
                    .total(lineTotal)
                    .build());
        }

        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount().setScale(2, ROUNDING) : BigDecimal.ZERO.setScale(2, ROUNDING);
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Discount amount cannot be negative");
        }
        if (discount.compareTo(subtotal.add(taxTotal)) > 0) {
            throw new BadRequestException("Discount cannot exceed total invoice value");
        }

        BigDecimal grandTotal = subtotal.add(taxTotal).subtract(discount).setScale(2, ROUNDING);
        String invoiceNumber = "INV-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        User currentUser = authz.getCurrentUser();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .patientId(patientId)
                .patientName(patientName)
                .currency(DEFAULT_CURRENCY)
                .items(lineItems)
                .subtotal(subtotal)
                .discount(discount)
                .tax(taxTotal)
                .totalAmount(grandTotal)
                .paidAmount(BigDecimal.ZERO.setScale(2, ROUNDING))
                .outstandingAmount(grandTotal)
                .status(InvoiceStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdByUserId(currentUser.getId())
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .date(Instant.now().toString().substring(0, 10))
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Mark charges as INVOICED
        for (BillingCharge charge : charges) {
            charge.setChargeStatus(ChargeStatus.INVOICED);
            charge.setInvoiceId(savedInvoice.getId());
            charge.setUpdatedAt(Instant.now());
            chargeRepository.save(charge);
        }

        auditService.audit(patientId, "Invoice", savedInvoice.getId(),
                ClinicalAuditAction.INVOICE_DRAFT_CREATED, "SUCCESS", null,
                "Created draft invoice " + savedInvoice.getInvoiceNumber() + " with total " + savedInvoice.getTotalAmount());

        return savedInvoice;
    }

    @Override
    @Transactional
    public Invoice issueInvoice(String invoiceId) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can issue invoices");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceImmutableException("INVOICE_ALREADY_ISSUED", "Cannot issue invoice in status: " + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        invoice.setDueAt(Instant.now().plus(30, ChronoUnit.DAYS));
        invoice.setUpdatedAt(Instant.now());

        Invoice saved = invoiceRepository.save(invoice);

        // Record initial ledger charge
        BillingLedgerEntry ledgerEntry = BillingLedgerEntry.builder()
                .entryNumber("LEDG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999))
                .invoiceId(saved.getId())
                .patientId(saved.getPatientId())
                .entryType(LedgerEntryType.CHARGE)
                .amount(saved.getTotalAmount())
                .currency(saved.getCurrency())
                .referenceType("INVOICE_ISSUE")
                .referenceId(saved.getInvoiceNumber())
                .occurredAt(Instant.now())
                .actorUserId(authz.getCurrentUser().getId())
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        auditService.audit(saved.getPatientId(), "Invoice", saved.getId(),
                ClinicalAuditAction.INVOICE_ISSUED, "SUCCESS", null,
                "Issued invoice " + saved.getInvoiceNumber() + " for total " + saved.getTotalAmount());

        publishOutboxNotification(saved.getPatientId(), "INVOICE_ISSUED", saved.getId(),
                "New Hospital Invoice Issued",
                "Invoice " + saved.getInvoiceNumber() + " for " + saved.getCurrency() + " " + saved.getTotalAmount() + " is ready for review.",
                "/patient/billing");

        return saved;
    }

    @Override
    @Transactional
    public Invoice voidInvoice(String invoiceId, String reason) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can void invoices");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            return invoice;
        }

        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Cannot void invoice with confirmed payments. Process a refund or credit note instead.");
        }

        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setOutstandingAmount(BigDecimal.ZERO.setScale(2, ROUNDING));
        invoice.setUpdatedAt(Instant.now());
        Invoice saved = invoiceRepository.save(invoice);

        // Release charges back to PENDING so they can be re-invoiced or corrected
        List<BillingCharge> charges = chargeRepository.findByInvoiceId(invoice.getId());
        for (BillingCharge c : charges) {
            c.setChargeStatus(ChargeStatus.PENDING);
            c.setInvoiceId(null);
            c.setUpdatedAt(Instant.now());
            chargeRepository.save(c);
        }

        auditService.audit(saved.getPatientId(), "Invoice", saved.getId(),
                ClinicalAuditAction.INVOICE_VOIDED, "SUCCESS", null,
                "Voided invoice " + saved.getInvoiceNumber() + " Reason: " + reason);

        publishOutboxNotification(saved.getPatientId(), "INVOICE_VOIDED", saved.getId(),
                "Hospital Invoice Voided",
                "Invoice " + saved.getInvoiceNumber() + " has been voided. Reason: " + reason,
                "/patient/billing");

        return saved;
    }

    @Override
    public Invoice getInvoiceById(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        authz.checkInvoiceAccess(invoice);
        return invoice;
    }

    @Override
    public Page<Invoice> getAllInvoices(Pageable pageable, InvoiceStatus status) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can list all invoices");
        }
        if (status != null) {
            return invoiceRepository.findByStatus(status, pageable);
        }
        return invoiceRepository.findAll(pageable);
    }

    @Override
    public Page<Invoice> getPatientInvoices(String patientId, Pageable pageable) {
        if (!authz.canAccessPatient(patientId) && !authz.canManageBilling()) {
            throw new AccessDeniedException("Not authorized to view patient invoices");
        }
        return invoiceRepository.findByPatientId(patientId, pageable);
    }

    // =========================================================================
    // 4. Payments, Concurrency & Ledger
    // =========================================================================

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BillingPayment recordPayment(RecordPaymentRequest request) {
        if (!authz.canRecordPayments()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can record payments");
        }

        // 1. Idempotency Check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<BillingPayment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                BillingPayment p = existing.get();
                if (p.getAmount().compareTo(request.getAmount().setScale(2, ROUNDING)) == 0 &&
                    p.getInvoiceId().equals(request.getInvoiceId())) {
                    return p;
                }
                throw new PaymentConflictException("IDEMPOTENCY_CONFLICT",
                        "Idempotency key '" + request.getIdempotencyKey() + "' already used with different parameters");
            }
        }

        // 2. Duplicate External Reference Check (for non-cash payments)
        if (request.getMethod() != PaymentMethod.CASH && request.getExternalReference() != null && !request.getExternalReference().isBlank()) {
            Optional<BillingPayment> dupRef = paymentRepository.findByExternalReference(request.getExternalReference().trim());
            if (dupRef.isPresent()) {
                throw new PaymentConflictException("DUPLICATE_EXTERNAL_REF",
                        "External payment reference already exists: " + request.getExternalReference());
            }
        }

        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + request.getInvoiceId()));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new BadRequestException("Cannot accept payment on VOID invoice: " + invoice.getInvoiceNumber());
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BadRequestException("Cannot accept payment on DRAFT invoice. Issue invoice first.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new OverpaymentException("INVOICE_ALREADY_PAID", "Invoice " + invoice.getInvoiceNumber() + " is already paid in full");
        }

        BigDecimal payAmount = request.getAmount().setScale(2, ROUNDING);
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than zero");
        }

        if (payAmount.compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new OverpaymentException("OVERPAYMENT_REJECTED",
                    "Payment of " + payAmount + " exceeds outstanding balance of " + invoice.getOutstandingAmount());
        }

        // 3. ATOMIC CONDITIONAL UPDATE VIA MONGO TEMPLATE (Concurrency Priority P0)
        org.bson.types.Decimal128 decPayAmount = new org.bson.types.Decimal128(payAmount);
        org.bson.types.Decimal128 decNegPayAmount = new org.bson.types.Decimal128(payAmount.negate());

        Query query = Query.query(
                Criteria.where("_id").is(invoice.getId())
                        .and("status").in(List.of(InvoiceStatus.ISSUED.name(), InvoiceStatus.PARTIALLY_PAID.name(), InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID))
                        .and("outstandingAmount").gte(decPayAmount)
        );
        Update update = new Update()
                .inc("paidAmount", decPayAmount)
                .inc("outstandingAmount", decNegPayAmount)
                .set("paymentMethod", request.getMethod())
                .set("updatedAt", Instant.now());

        UpdateResult result = mongoTemplate.updateFirst(query, update, Invoice.class);
        if (result.getModifiedCount() == 0) {
            log.warn("Concurrent payment conflict detected on invoice {}. Outstanding balance insufficient.", invoice.getInvoiceNumber());
            throw new PaymentConflictException("CONCURRENT_PAYMENT_CONFLICT",
                    "Concurrent balance update conflict on invoice " + invoice.getInvoiceNumber() + ". Please retry.");
        }

        // Coordinate state transition without invoice status race
        applyInvoiceStatusTransition(invoice.getId());
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElse(invoice);

        // 4. Create Confirmed Payment Record
        User currentUser = authz.getCurrentUser();
        String paymentNumber = "PAY-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        BillingPayment payment = BillingPayment.builder()
                .paymentNumber(paymentNumber)
                .invoiceId(invoice.getId())
                .patientId(invoice.getPatientId())
                .amount(payAmount)
                .refundableAmount(payAmount)
                .refundedAmount(BigDecimal.ZERO.setScale(2, ROUNDING))
                .currency(invoice.getCurrency())
                .method(request.getMethod())
                .status(PaymentStatus.CONFIRMED)
                .externalReference(request.getExternalReference())
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .receivedAt(Instant.now())
                .confirmedAt(Instant.now())
                .confirmedByUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .notes(request.getNotes())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BillingPayment savedPayment;
        try {
            savedPayment = paymentRepository.save(payment);

            // 5. Create Immutable Ledger Entry
            BillingLedgerEntry ledgerEntry = BillingLedgerEntry.builder()
                    .entryNumber("LEDG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999))
                    .invoiceId(invoice.getId())
                    .paymentId(savedPayment.getId())
                    .patientId(invoice.getPatientId())
                    .entryType(LedgerEntryType.PAYMENT)
                    .amount(payAmount)
                    .currency(invoice.getCurrency())
                    .referenceType("PAYMENT")
                    .referenceId(savedPayment.getPaymentNumber())
                    .occurredAt(Instant.now())
                    .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                    .idempotencyKey(request.getIdempotencyKey())
                    .build();
            ledgerEntryRepository.save(ledgerEntry);
        } catch (Throwable persistenceFailure) {
            log.error("CRITICAL: Downstream persistence failed after deducting balance for invoice {}. Rolling back invoice balance deduction.",
                    invoice.getInvoiceNumber(), persistenceFailure);
            try {
                // Compensating rollback: restore deducted balance atomically
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(invoice.getId())),
                        new Update()
                                .inc("paidAmount", decNegPayAmount)
                                .inc("outstandingAmount", decPayAmount)
                                .set("updatedAt", Instant.now()),
                        Invoice.class
                );
                // Also revert any invoice status transition
                revertInvoiceStatusAfterRollback(invoice.getId());
            } catch (Throwable rollbackError) {
                log.error("FATAL: Compensating balance rollback failed for invoice {}! Manual reconciliation mandatory.",
                        invoice.getInvoiceNumber(), rollbackError);
            }
            if (persistenceFailure instanceof RuntimeException) {
                throw (RuntimeException) persistenceFailure;
            }
            throw new RuntimeException("Payment persistence failed; invoice balance reverted.", persistenceFailure);
        }

        // 6. Audit & Outbox Notifications
        auditService.audit(invoice.getPatientId(), "BillingPayment", savedPayment.getId(),
                ClinicalAuditAction.PAYMENT_CONFIRMED, "SUCCESS", null,
                "Confirmed payment " + savedPayment.getPaymentNumber() + " of " + payAmount + " for invoice " + invoice.getInvoiceNumber());

        publishOutboxNotification(invoice.getPatientId(), "PAYMENT_CONFIRMED", savedPayment.getId(),
                "Payment Confirmed",
                "Your payment of " + savedPayment.getCurrency() + " " + payAmount + " for invoice " + invoice.getInvoiceNumber() + " has been confirmed.",
                "/patient/billing");

        if (updatedInvoice.getStatus() == InvoiceStatus.PAID) {
            publishOutboxNotification(invoice.getPatientId(), "INVOICE_PAID", invoice.getId(),
                    "Invoice Paid in Full",
                    "Invoice " + invoice.getInvoiceNumber() + " is now fully settled.",
                    "/patient/billing");
        }

        return savedPayment;
    }

    private InvoiceStatus applyInvoiceStatusTransition(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) return null;

        BigDecimal outstanding = invoice.getOutstandingAmount() != null ? invoice.getOutstandingAmount() : BigDecimal.ZERO;
        org.bson.types.Decimal128 zeroDec = new org.bson.types.Decimal128(BigDecimal.ZERO);

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            Query query = Query.query(Criteria.where("_id").is(invoiceId));
            Update update = new Update().set("status", InvoiceStatus.PAID).set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query, update, Invoice.class);
            return InvoiceStatus.PAID;
        } else {
            // ONLY transition to PARTIALLY_PAID if current status is ISSUED. Never downgrade PAID to PARTIALLY_PAID!
            Query query = Query.query(
                    Criteria.where("_id").is(invoiceId)
                            .and("status").in(List.of(InvoiceStatus.ISSUED.name(), InvoiceStatus.ISSUED))
                            .and("outstandingAmount").gt(zeroDec)
            );
            Update update = new Update().set("status", InvoiceStatus.PARTIALLY_PAID).set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query, update, Invoice.class);
            return InvoiceStatus.PARTIALLY_PAID;
        }
    }

    private void revertInvoiceStatusAfterRollback(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) return;

        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(invoiceId)),
                    new Update().set("status", InvoiceStatus.ISSUED).set("updatedAt", Instant.now()),
                    Invoice.class
            );
        } else {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(invoiceId)),
                    new Update().set("status", InvoiceStatus.PARTIALLY_PAID).set("updatedAt", Instant.now()),
                    Invoice.class
            );
        }
    }

    @Override
    @Transactional
    public BillingPayment confirmPayment(String paymentId) {
        if (!authz.canRecordPayments()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can confirm payments");
        }
        BillingPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Idempotency: if already confirmed, return without re-debiting balance or rewriting confirmedAt
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            log.info("Payment {} is already CONFIRMED. Idempotent return.", payment.getPaymentNumber());
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Only PENDING payments can be confirmed. Current status: " + payment.getStatus());
        }

        User currentUser = authz.getCurrentUser();
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + payment.getInvoiceId()));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new BadRequestException("Cannot confirm payment on VOID invoice: " + invoice.getInvoiceNumber());
        }

        BigDecimal payAmount = payment.getAmount().setScale(2, ROUNDING);
        org.bson.types.Decimal128 decPayAmount = new org.bson.types.Decimal128(payAmount);
        org.bson.types.Decimal128 decNegPayAmount = new org.bson.types.Decimal128(payAmount.negate());

        // Atomically deduct invoice balance
        Query query = Query.query(
                Criteria.where("_id").is(invoice.getId())
                        .and("status").in(List.of(InvoiceStatus.ISSUED.name(), InvoiceStatus.PARTIALLY_PAID.name(), InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID))
                        .and("outstandingAmount").gte(decPayAmount)
        );
        Update update = new Update()
                .inc("paidAmount", decPayAmount)
                .inc("outstandingAmount", decNegPayAmount)
                .set("paymentMethod", payment.getMethod())
                .set("updatedAt", Instant.now());

        UpdateResult result = mongoTemplate.updateFirst(query, update, Invoice.class);
        if (result.getModifiedCount() == 0) {
            throw new PaymentConflictException("CONCURRENT_PAYMENT_CONFLICT",
                    "Invoice balance insufficient to confirm payment " + payment.getPaymentNumber());
        }

        // Atomically transition payment status PENDING -> CONFIRMED
        Query payQuery = Query.query(Criteria.where("_id").is(payment.getId()).and("status").is(PaymentStatus.PENDING));
        Update payUpdate = new Update()
                .set("status", PaymentStatus.CONFIRMED)
                .set("confirmedAt", Instant.now())
                .set("confirmedByUserId", currentUser.getId())
                .set("refundableAmount", decPayAmount)
                .set("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .set("updatedAt", Instant.now());

        UpdateResult payRes = mongoTemplate.updateFirst(payQuery, payUpdate, BillingPayment.class);
        if (payRes.getModifiedCount() == 0) {
            // Rollback invoice deduction if payment was concurrently modified
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(invoice.getId())),
                    new Update().inc("paidAmount", decNegPayAmount).inc("outstandingAmount", decPayAmount),
                    Invoice.class
            );
            return paymentRepository.findById(payment.getId()).orElse(payment);
        }

        // Coordinate invoice state transition
        applyInvoiceStatusTransition(invoice.getId());

        // Create immutable ledger entry
        BillingLedgerEntry ledgerEntry = BillingLedgerEntry.builder()
                .entryNumber("LEDG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999))
                .invoiceId(invoice.getId())
                .paymentId(payment.getId())
                .patientId(invoice.getPatientId())
                .entryType(LedgerEntryType.PAYMENT)
                .amount(payAmount)
                .currency(invoice.getCurrency())
                .referenceType("PAYMENT")
                .referenceId(payment.getPaymentNumber())
                .occurredAt(Instant.now())
                .actorUserId(currentUser.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        auditService.audit(invoice.getPatientId(), "BillingPayment", payment.getId(),
                ClinicalAuditAction.PAYMENT_CONFIRMED, "SUCCESS", null,
                "Confirmed payment " + payment.getPaymentNumber() + " of " + payAmount + " for invoice " + invoice.getInvoiceNumber());

        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    @Override
    public BillingPayment getPaymentById(String paymentId) {
        BillingPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        authz.checkPaymentAccess(payment);
        return payment;
    }

    @Override
    public List<BillingPayment> getPaymentsByInvoice(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        authz.checkInvoiceAccess(invoice);
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    @Override
    public Page<BillingPayment> getPatientPayments(String patientId, Pageable pageable) {
        if (!authz.canAccessPatient(patientId) && !authz.canManageBilling()) {
            throw new AccessDeniedException("Not authorized to view patient payments");
        }
        return paymentRepository.findByPatientId(patientId, pageable);
    }

    @Override
    public List<BillingLedgerEntry> getLedgerEntriesByInvoice(String invoiceId) {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can view ledger entries");
        }
        return ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoiceId);
    }

    // =========================================================================
    // 5. Refunds & Credit Notes
    // =========================================================================

    @Override
    @Transactional
    public BillingRefund processRefund(ProcessRefundRequest request) {
        if (!authz.canProcessRefunds()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can process refunds");
        }

        BillingPayment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + request.getPaymentId()));

        if (payment.getStatus() != PaymentStatus.CONFIRMED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Only CONFIRMED or PARTIALLY_REFUNDED payments can be refunded");
        }

        BigDecimal refundAmount = request.getAmount().setScale(2, ROUNDING);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero");
        }

        // Initialize refundableAmount if not set on the document
        if (payment.getRefundableAmount() == null) {
            List<BillingRefund> existingRefunds = refundRepository.findByPaymentId(payment.getId());
            BigDecimal alreadyRefunded = existingRefunds.stream()
                    .map(BillingRefund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainingRefundable = payment.getAmount().subtract(alreadyRefunded);

            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(payment.getId()).and("refundableAmount").exists(false)),
                    new Update()
                            .set("refundableAmount", new org.bson.types.Decimal128(remainingRefundable))
                            .set("refundedAmount", new org.bson.types.Decimal128(alreadyRefunded)),
                    BillingPayment.class
            );
        }

        org.bson.types.Decimal128 decRefund = new org.bson.types.Decimal128(refundAmount);
        org.bson.types.Decimal128 decNegRefund = new org.bson.types.Decimal128(refundAmount.negate());

        // Atomic conditional decrement on Payment refundableAmount (Step 5 Concurrency Protection)
        Query refQuery = Query.query(
                Criteria.where("_id").is(payment.getId())
                        .and("status").in(List.of(PaymentStatus.CONFIRMED.name(), PaymentStatus.PARTIALLY_REFUNDED.name(), PaymentStatus.CONFIRMED, PaymentStatus.PARTIALLY_REFUNDED))
                        .and("refundableAmount").gte(decRefund)
        );
        Update refUpdate = new Update()
                .inc("refundableAmount", decNegRefund)
                .inc("refundedAmount", decRefund)
                .set("updatedAt", Instant.now());

        UpdateResult refRes = mongoTemplate.updateFirst(refQuery, refUpdate, BillingPayment.class);
        if (refRes.getModifiedCount() == 0) {
            throw new PaymentConflictException("CONCURRENT_REFUND_CONFLICT",
                    "Requested refund " + refundAmount + " exceeds available refundable balance or concurrent conflict occurred");
        }

        // Check if fully or partially refunded and update payment status
        BillingPayment currentPayment = paymentRepository.findById(payment.getId()).orElse(payment);
        if (currentPayment.getRefundableAmount() != null &&
            currentPayment.getRefundableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(payment.getId())),
                    new Update().set("status", PaymentStatus.REFUNDED),
                    BillingPayment.class
            );
        } else {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(payment.getId())),
                    new Update().set("status", PaymentStatus.PARTIALLY_REFUNDED),
                    BillingPayment.class
            );
        }

        User currentUser = authz.getCurrentUser();
        String refundNumber = "REF-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        BillingRefund refund = BillingRefund.builder()
                .refundNumber(refundNumber)
                .paymentId(payment.getId())
                .invoiceId(payment.getInvoiceId())
                .patientId(payment.getPatientId())
                .amount(refundAmount)
                .currency(payment.getCurrency())
                .reasonCode(request.getReasonCode())
                .externalReference(request.getExternalReference())
                .authorizedByUserId(currentUser.getId())
                .createdAt(Instant.now())
                .build();

        BillingRefund savedRefund = refundRepository.save(refund);

        // Record compensating ledger entry
        BillingLedgerEntry ledgerEntry = BillingLedgerEntry.builder()
                .entryNumber("LEDG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999))
                .invoiceId(payment.getInvoiceId())
                .paymentId(payment.getId())
                .patientId(payment.getPatientId())
                .entryType(LedgerEntryType.REFUND)
                .amount(refundAmount.negate())
                .currency(payment.getCurrency())
                .referenceType("REFUND")
                .referenceId(savedRefund.getRefundNumber())
                .occurredAt(Instant.now())
                .actorUserId(currentUser.getId())
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        // Restore invoice outstanding balance atomically via MongoTemplate (no version conflict)
        Query invRefQuery = Query.query(Criteria.where("_id").is(payment.getInvoiceId()));
        Update invRefUpdate = new Update()
                .inc("paidAmount", decNegRefund)
                .inc("outstandingAmount", decRefund)
                .set("status", InvoiceStatus.PARTIALLY_PAID)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(invRefQuery, invRefUpdate, Invoice.class);

        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
        String invoiceNumber = invoice != null ? invoice.getInvoiceNumber() : payment.getInvoiceId();

        auditService.audit(payment.getPatientId(), "BillingRefund", savedRefund.getId(),
                ClinicalAuditAction.PAYMENT_REFUNDED, "SUCCESS", null,
                "Processed refund " + savedRefund.getRefundNumber() + " for " + refundAmount);

        publishOutboxNotification(payment.getPatientId(), "PAYMENT_REFUNDED", savedRefund.getId(),
                "Payment Refund Processed",
                "A refund of " + savedRefund.getCurrency() + " " + refundAmount + " has been processed for invoice " + invoiceNumber,
                "/patient/billing");

        return savedRefund;
    }

    @Override
    @Transactional
    public BillingCreditNote issueCreditNote(IssueCreditNoteRequest request) {
        if (!authz.canProcessRefunds()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can issue credit notes");
        }

        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + request.getInvoiceId()));

        BigDecimal creditAmount = request.getAmount().setScale(2, ROUNDING);
        if (creditAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Credit note amount must be greater than zero");
        }
        if (creditAmount.compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new BadRequestException("Credit note amount cannot exceed invoice outstanding balance: " + invoice.getOutstandingAmount());
        }

        User currentUser = authz.getCurrentUser();
        String cnNumber = "CN-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);

        BillingCreditNote creditNote = BillingCreditNote.builder()
                .creditNoteNumber(cnNumber)
                .invoiceId(invoice.getId())
                .patientId(invoice.getPatientId())
                .amount(creditAmount)
                .currency(invoice.getCurrency())
                .reasonCode(request.getReasonCode())
                .notes(request.getNotes())
                .issuedByUserId(currentUser.getId())
                .issuedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        BillingCreditNote saved = creditNoteRepository.save(creditNote);

        // Compensating ledger entry
        BillingLedgerEntry ledgerEntry = BillingLedgerEntry.builder()
                .entryNumber("LEDG-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999))
                .invoiceId(invoice.getId())
                .patientId(invoice.getPatientId())
                .entryType(LedgerEntryType.CREDIT_NOTE)
                .amount(creditAmount.negate())
                .currency(invoice.getCurrency())
                .referenceType("CREDIT_NOTE")
                .referenceId(saved.getCreditNoteNumber())
                .occurredAt(Instant.now())
                .actorUserId(currentUser.getId())
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        // Reduce invoice balance
        invoice.setOutstandingAmount(invoice.getOutstandingAmount().subtract(creditAmount).setScale(2, ROUNDING));
        invoice.setDiscount(invoice.getDiscount() != null ? invoice.getDiscount().add(creditAmount).setScale(2, ROUNDING) : creditAmount);
        invoice.setTotalAmount(invoice.getTotalAmount().subtract(creditAmount).setScale(2, ROUNDING));
        if (invoice.getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        }
        invoice.setUpdatedAt(Instant.now());
        invoiceRepository.save(invoice);

        auditService.audit(invoice.getPatientId(), "BillingCreditNote", saved.getId(),
                ClinicalAuditAction.CREDIT_NOTE_ISSUED, "SUCCESS", null,
                "Issued credit note " + saved.getCreditNoteNumber() + " for " + creditAmount);

        return saved;
    }

    @Override
    public List<BillingRefund> getRefundsByInvoice(String invoiceId) {
        return refundRepository.findByInvoiceId(invoiceId);
    }

    @Override
    public List<BillingCreditNote> getCreditNotesByInvoice(String invoiceId) {
        return creditNoteRepository.findByInvoiceId(invoiceId);
    }

    // =========================================================================
    // 6. Reports & Reconciliation
    // =========================================================================

    @Override
    public BillingSummaryDTO getBillingSummary() {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can view billing summary");
        }

        List<Invoice> allInvoices = invoiceRepository.findAll();
        long draft = 0;
        long issued = 0;
        long partial = 0;
        long paid = 0;
        long voided = 0;

        BigDecimal totalBilled = BigDecimal.ZERO.setScale(2, ROUNDING);
        BigDecimal totalCollected = BigDecimal.ZERO.setScale(2, ROUNDING);
        BigDecimal totalOutstanding = BigDecimal.ZERO.setScale(2, ROUNDING);

        for (Invoice inv : allInvoices) {
            InvoiceStatus st = inv.getStatus() != null ? inv.getStatus() : InvoiceStatus.ISSUED;
            switch (st) {
                case DRAFT -> draft++;
                case ISSUED -> issued++;
                case PARTIALLY_PAID -> partial++;
                case PAID -> paid++;
                case VOID -> voided++;
            }

            if (st != InvoiceStatus.VOID && st != InvoiceStatus.DRAFT) {
                if (inv.getTotalAmount() != null) totalBilled = totalBilled.add(inv.getTotalAmount());
                if (inv.getPaidAmount() != null) totalCollected = totalCollected.add(inv.getPaidAmount());
                if (inv.getOutstandingAmount() != null) totalOutstanding = totalOutstanding.add(inv.getOutstandingAmount());
            }
        }

        return BillingSummaryDTO.builder()
                .totalInvoices(allInvoices.size())
                .draftInvoices(draft)
                .issuedInvoices(issued)
                .partiallyPaidInvoices(partial)
                .paidInvoices(paid)
                .voidInvoices(voided)
                .totalBilledAmount(totalBilled)
                .totalCollectedAmount(totalCollected)
                .totalOutstandingAmount(totalOutstanding)
                .currency(DEFAULT_CURRENCY)
                .build();
    }

    @Override
    public ReconciliationReportDTO getReconciliationReport() {
        if (!authz.canManageBilling()) {
            throw new AccessDeniedException("Only Billing Staff and Administrators can run reconciliation reports");
        }

        List<BillingCharge> uninvoicedCharges = chargeRepository.findAll().stream()
                .filter(c -> c.getChargeStatus() == ChargeStatus.PENDING)
                .collect(Collectors.toList());

        List<Invoice> allInvoices = invoiceRepository.findAll();
        List<String> warnings = new ArrayList<>();
        long inconsistentBalances = 0;

        for (Invoice inv : allInvoices) {
            if (inv.getStatus() == InvoiceStatus.VOID) continue;
            BigDecimal billed = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal outstanding = inv.getOutstandingAmount() != null ? inv.getOutstandingAmount() : BigDecimal.ZERO;

            if (paid.add(outstanding).compareTo(billed) != 0) {
                inconsistentBalances++;
                warnings.add("Invoice " + inv.getInvoiceNumber() + " balance mismatch: billed " + billed + ", paid " + paid + ", outstanding " + outstanding);
            }
        }

        boolean healthy = uninvoicedCharges.isEmpty() && inconsistentBalances == 0;

        return ReconciliationReportDTO.builder()
                .uninvoicedChargesCount(uninvoicedCharges.size())
                .inconsistentInvoiceBalancesCount(inconsistentBalances)
                .missingLedgerEntriesCount(0)
                .warningDetails(warnings)
                .healthy(healthy)
                .build();
    }

    // =========================================================================
    // 7. PDF Generation
    // =========================================================================

    @Override
    public byte[] generateInvoicePdf(String invoiceId) {
        Invoice invoice = getInvoiceById(invoiceId);
        User patientUser = resolveUser(invoice.getPatientId());
        List<BillingPayment> payments = paymentRepository.findByInvoiceId(invoiceId);
        return billingPdfService.generateInvoicePdf(invoice, patientUser, payments);
    }

    @Override
    public byte[] generateReceiptPdf(String paymentId) {
        BillingPayment payment = getPaymentById(paymentId);
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
        User patientUser = resolveUser(payment.getPatientId());
        User cashier = payment.getConfirmedByUserId() != null ? resolveUser(payment.getConfirmedByUserId()) : null;
        return billingPdfService.generateReceiptPdf(payment, invoice, patientUser, cashier);
    }

    @Override
    public byte[] generateCreditNotePdf(String creditNoteId) {
        BillingCreditNote creditNote = creditNoteRepository.findById(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit note not found: " + creditNoteId));
        Invoice invoice = invoiceRepository.findById(creditNote.getInvoiceId()).orElse(null);
        User patientUser = resolveUser(creditNote.getPatientId());
        return billingPdfService.generateCreditNotePdf(creditNote, invoice, patientUser);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private User resolveUser(String patientIdOrUserId) {
        if (patientIdOrUserId == null) return null;
        return userRepository.findById(patientIdOrUserId)
                .or(() -> patientRepository.findById(patientIdOrUserId).flatMap(p -> userRepository.findById(p.getUserId())))
                .orElse(null);
    }

    private void publishOutboxNotification(String patientIdOrUserId, String eventType, String aggregateId,
                                           String title, String message, String actionUrl) {
        try {
            User patientUser = resolveUser(patientIdOrUserId);
            String recipientUserId = patientUser != null ? patientUser.getId() : patientIdOrUserId;

            NotificationOutbox inApp = NotificationOutbox.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .recipientUserId(recipientUserId)
                    .recipientRole("PATIENT")
                    .channel(OutboxChannel.IN_APP)
                    .payloadVersion(1)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(Instant.now())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .deduplicationKey("billing:" + eventType.toLowerCase() + ":" + aggregateId + ":" + recipientUserId)
                    .title(title)
                    .message(message)
                    .actionUrl(actionUrl)
                    .metadata(Map.of("aggregateId", aggregateId, "eventType", eventType))
                    .build();

            outboxRepository.save(inApp);
        } catch (Exception e) {
            log.warn("Failed to publish outbox notification for billing event {}: {}", eventType, e.getMessage());
        }
    }
}
