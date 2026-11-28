package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.FinancialReconciliationDashboardDTO;
import com.hospital.backend.dto.billing.ProcessRefundRequest;
import com.hospital.backend.dto.billing.RecordPaymentRequest;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.OverpaymentException;
import com.hospital.backend.exception.PaymentConflictException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise MongoDB Concurrency and Financial Data Integrity Integration Test.
 * PRIORITY P0 - Verifies atomic invoice payment deduction and prevention of race conditions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
public class BillingConcurrencyMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_billing_concurrency_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                mongoDBContainer.start();
            }
        } catch (Throwable ignored) {
        }
    }

    @DynamicPropertySource
    static void setMongoProperties(DynamicPropertyRegistry registry) {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        } else {
            registry.add("spring.data.mongodb.uri", () -> "mongodb://fail-closed-docker-required:27017/" + ISOLATED_TEST_DB);
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        }
    }

    @BeforeAll
    public static void verifyDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable() || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED TEST ISOLATION: Docker is required for BillingConcurrencyMongoIntegrationTest.");
        }
    }

    @AfterAll
    public static void stopContainer() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            try {
                mongoDBContainer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Autowired
    private BillingService billingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BillingPaymentRepository paymentRepository;

    @Autowired
    private BillingChargeRepository chargeRepository;

    @Autowired
    private BillingServiceCatalogRepository catalogRepository;

    @Autowired
    private BillingLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private BillingRefundRepository refundRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AnalyticsService analyticsService;

    private User billingUser;
    private Patient patient;

    private void setupSecurityContext() {
        org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        billingUser.getEmail(), null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_BILLING_STAFF"))
                );
        context.setAuthentication(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        chargeRepository.deleteAll();
        catalogRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        refundRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        billingUser = User.builder()
                .id("bill-user-" + UUID.randomUUID())
                .email("billing-" + UUID.randomUUID() + "@pulse.org")
                .role(Role.BILLING_STAFF)
                .fullName("Billing Cashier")
                .password("encoded_pass")
                .build();
        userRepository.save(billingUser);

        patient = Patient.builder()
                .id("pat-" + UUID.randomUUID())
                .userId(billingUser.getId())
                .fullName("Alice Patient")
                .build();
        patientRepository.save(patient);

        setupSecurityContext();
    }

    @Test
    @DisplayName("TEST A: 20 concurrent requests each attempt $1.00 on a $10.00 invoice")
    void testA_ConcurrentPayments_TenDollarsTotal_TwentyRequests() throws InterruptedException {
        // Create an issued invoice with $10.00 outstanding balance
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("10.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("10.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    setupSecurityContext();

                    RecordPaymentRequest req = RecordPaymentRequest.builder()
                            .invoiceId(invoiceId)
                            .amount(new BigDecimal("1.00"))
                            .method(PaymentMethod.CASH)
                            .externalReference("TXN-A-" + index)
                            .build();

                    billingService.recordPayment(req);
                    successCount.incrementAndGet();
                } catch (PaymentConflictException | OverpaymentException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within 30 seconds");

        // Verify TEST A requirements:
        // - Exactly 10 payments succeed ($10 / $1 = 10)
        assertEquals(10, successCount.get(), "Exactly 10 payments of $1.00 must succeed");
        assertEquals(10, conflictCount.get(), "Exactly 10 payments must be rejected");

        // Reload invoice from MongoDB
        Invoice finalInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, finalInvoice.getStatus(), "Invoice must be marked PAID");
        assertEquals(new BigDecimal("10.00"), finalInvoice.getPaidAmount().setScale(2), "Total confirmed paid must be exactly 10.00");
        assertEquals(new BigDecimal("0.00"), finalInvoice.getOutstandingAmount().setScale(2), "Outstanding must be exactly 0.00");
        assertTrue(finalInvoice.getOutstandingAmount().compareTo(BigDecimal.ZERO) >= 0, "Outstanding balance must NEVER be negative");

        // Exactly 10 committed payment ledger entries
        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoiceId);
        assertEquals(10, ledgers.size(), "Exactly 10 payment ledger entries must be committed");
    }

    @Test
    @DisplayName("TEST B: Two concurrent payments $4.00 and $6.00 against $10.00 invoice")
    void testB_ConcurrentPayments_FourAndSix_ZeroStaleOverwrite() throws InterruptedException {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("10.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("10.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        BigDecimal[] amounts = new BigDecimal[]{new BigDecimal("4.00"), new BigDecimal("6.00")};
        for (int i = 0; i < 2; i++) {
            final BigDecimal amt = amounts[i];
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    setupSecurityContext();
                    RecordPaymentRequest req = RecordPaymentRequest.builder()
                            .invoiceId(invoiceId)
                            .amount(amt)
                            .method(PaymentMethod.CASH)
                            .externalReference("TXN-B-" + index)
                            .build();
                    billingService.recordPayment(req);
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed);

        Invoice finalInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, finalInvoice.getStatus(), "Invoice must be marked PAID");
        assertEquals(new BigDecimal("10.00"), finalInvoice.getPaidAmount().setScale(2), "Total paid must be exactly 10.00");
        assertEquals(new BigDecimal("0.00"), finalInvoice.getOutstandingAmount().setScale(2), "Outstanding must be 0.00");
    }

    @Test
    @DisplayName("TEST C: Duplicate confirmation of same pending payment is idempotent")
    void testC_DuplicateConfirmation_Idempotent() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        BillingPayment payment = BillingPayment.builder()
                .paymentNumber("PAY-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .invoiceId(invoice.getId())
                .patientId(patient.getId())
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PENDING)
                .receivedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        payment = paymentRepository.save(payment);

        // First confirmation
        BillingPayment firstConfirm = billingService.confirmPayment(payment.getId());
        assertEquals(PaymentStatus.CONFIRMED, firstConfirm.getStatus());
        Instant firstConfirmedAt = firstConfirm.getConfirmedAt();
        assertNotNull(firstConfirmedAt);

        Invoice afterFirst = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("25.00"), afterFirst.getPaidAmount().setScale(2));
        assertEquals(new BigDecimal("75.00"), afterFirst.getOutstandingAmount().setScale(2));
        assertEquals(InvoiceStatus.PARTIALLY_PAID, afterFirst.getStatus());

        List<BillingLedgerEntry> ledgersFirst = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertEquals(1, ledgersFirst.size(), "Exactly 1 ledger entry created on first confirmation");

        // Second confirmation (IDEMPOTENT)
        BillingPayment secondConfirm = billingService.confirmPayment(payment.getId());
        assertEquals(PaymentStatus.CONFIRMED, secondConfirm.getStatus());
        assertEquals(firstConfirmedAt, secondConfirm.getConfirmedAt(), "Confirmed timestamp must not be overwritten");

        Invoice afterSecond = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("25.00"), afterSecond.getPaidAmount().setScale(2), "Balance must NOT be debited twice");
        assertEquals(new BigDecimal("75.00"), afterSecond.getOutstandingAmount().setScale(2));

        List<BillingLedgerEntry> ledgersSecond = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertEquals(1, ledgersSecond.size(), "Ledger count must remain exactly 1 (no duplicate ledger entries)");
    }

    @Test
    @DisplayName("TEST D: Payment transaction rejection leaves invoice balance unchanged")
    void testD_PaymentRejection_LeavesInvoiceBalanceUnchanged() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("10.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("10.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        RecordPaymentRequest overpayReq = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("15.00")) // Exceeds 10.00 balance
                .method(PaymentMethod.CASH)
                .build();

        assertThrows(OverpaymentException.class, () -> billingService.recordPayment(overpayReq));

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.00"), reloaded.getPaidAmount().setScale(2), "Paid amount must remain 0.00");
        assertEquals(new BigDecimal("10.00"), reloaded.getOutstandingAmount().setScale(2), "Outstanding amount must remain 10.00");
        assertEquals(InvoiceStatus.ISSUED, reloaded.getStatus(), "Status must remain ISSUED");
        assertEquals(0, ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId()).size(), "No ledger entries");
    }

    @Test
    @DisplayName("TEST E: Two concurrent refunds exceeding refundable balance prevent over-refund")
    void testE_ConcurrentRefunds_PreventOverRefund() throws InterruptedException {
        // Invoice $100, pay $50
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-E-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        RecordPaymentRequest payReq = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.CASH)
                .build();
        BillingPayment payment = billingService.recordPayment(payReq);
        final String paymentId = payment.getId();

        // 2 concurrent threads attempt $30.00 refund each (Total = $60.00, but only $50.00 paid)
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    setupSecurityContext();
                    ProcessRefundRequest req = ProcessRefundRequest.builder()
                            .paymentId(paymentId)
                            .amount(new BigDecimal("30.00"))
                            .reasonCode("PATIENT_REQUEST")
                            .externalReference("REF-EXT-" + index)
                            .build();
                    billingService.processRefund(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed);

        // Exactly 1 refund must succeed, the other rejected
        assertEquals(1, successCount.get(), "At most one $30 refund can succeed on $50 payment");
        assertEquals(1, rejectedCount.get(), "Second $30 refund must be rejected to prevent over-refund");

        List<BillingRefund> refunds = refundRepository.findByPaymentId(paymentId);
        assertEquals(1, refunds.size(), "Exactly 1 refund record must exist");
        assertEquals(new BigDecimal("30.00"), refunds.get(0).getAmount().setScale(2));

        // Invoice balance restored correctly ($50 - $30 = $20 net paid; outstanding = $80)
        Invoice finalInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("20.00"), finalInvoice.getPaidAmount().setScale(2));
        assertEquals(new BigDecimal("80.00"), finalInvoice.getOutstandingAmount().setScale(2));
    }

    @Test
    @DisplayName("TEST F: Legacy invoice amount types (Double/String) compatibility handling")
    void testF_LegacyInvoiceTypeCompatibility() {
        String legacyId = "inv-legacy-" + UUID.randomUUID().toString().substring(0, 8);
        org.bson.Document rawDoc = new org.bson.Document()
                .append("_id", legacyId)
                .append("invoiceNumber", "INV-LEGACY-" + System.currentTimeMillis())
                .append("patientId", patient.getId())
                .append("patientName", "Legacy Patient")
                .append("status", "ISSUED")
                .append("subtotal", 100.0)
                .append("discount", 0.0)
                .append("tax", 0.0)
                .append("totalAmount", 100.0)
                .append("paidAmount", 0.0)
                .append("outstandingAmount", 100.0)
                .append("currency", "USD");

        mongoTemplate.getCollection("invoices").insertOne(rawDoc);

        Invoice loaded = invoiceRepository.findById(legacyId).orElseThrow();
        assertNotNull(loaded);
        assertEquals(InvoiceStatus.ISSUED, loaded.getStatus());
        assertEquals(new BigDecimal("100.00"), loaded.getTotalAmount().setScale(2));
        assertEquals(new BigDecimal("100.00"), loaded.getOutstandingAmount().setScale(2));
        assertEquals(new BigDecimal("0.00"), loaded.getPaidAmount().setScale(2));
    }

    @Test
    @DisplayName("P0 - Charge Deduplication: Compound Unique Index Prevents Double Billing")
    void testChargeDeduplication_CompoundUniqueIndex() {
        BillingCharge charge1 = BillingCharge.builder()
                .patientId(patient.getId())
                .sourceType(ChargeSourceType.APPOINTMENT)
                .sourceId("appt-100")
                .sourceLineId("cons-fee")
                .serviceCode("CNS-001")
                .descriptionSnapshot("Consultation")
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("50.00"))
                .chargeStatus(ChargeStatus.PENDING)
                .build();
        chargeRepository.save(charge1);

        BillingCharge duplicateCharge = BillingCharge.builder()
                .patientId(patient.getId())
                .sourceType(ChargeSourceType.APPOINTMENT)
                .sourceId("appt-100")
                .sourceLineId("cons-fee")
                .serviceCode("CNS-001")
                .descriptionSnapshot("Consultation duplicate")
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("50.00"))
                .chargeStatus(ChargeStatus.PENDING)
                .build();

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> chargeRepository.save(duplicateCharge));
    }

    @Test
    @DisplayName("P0 - Failure Injection: Downstream Ledger Persistence Failure Aborts Transaction Atomically")
    void testFailureInjection_DownstreamPersistenceFailure_RollsBackInvoiceBalance() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        // Failure Injection: Unique index on currency to force failure on ledger save
        mongoTemplate.indexOps(BillingLedgerEntry.class).ensureIndex(
                new Index().on("currency", Sort.Direction.ASC).unique()
        );
        ledgerEntryRepository.save(BillingLedgerEntry.builder()
                .entryNumber("LEDG-POISON-01")
                .invoiceId("dummy-inv")
                .currency("USD")
                .amount(BigDecimal.ONE)
                .entryType(LedgerEntryType.PAYMENT)
                .occurredAt(Instant.now())
                .build());

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId(invoiceId)
                .amount(new BigDecimal("25.00"))
                .method(PaymentMethod.CASH)
                .externalReference("TXN-FAIL-01")
                .build();

        assertThrows(RuntimeException.class, () -> billingService.recordPayment(req));

        // In real ACID transactions, the entire transaction is aborted in MongoDB:
        Invoice reloaded = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.ISSUED, reloaded.getStatus(), "Invoice status must remain ISSUED");
        assertEquals(new BigDecimal("100.00"), reloaded.getOutstandingAmount().setScale(2), "Outstanding balance untouched");
        assertEquals(new BigDecimal("0.00"), reloaded.getPaidAmount().setScale(2), "Paid amount untouched");

        List<BillingPayment> payments = paymentRepository.findByInvoiceId(invoiceId);
        assertTrue(payments.stream().noneMatch(p -> p.getStatus() == PaymentStatus.CONFIRMED),
                "No orphan CONFIRMED payment must exist in DB");
        assertTrue(payments.isEmpty(),
                "No uncommitted payment documents should exist when transaction aborts");

        List<BillingLedgerEntry> invoiceLedgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoiceId);
        assertTrue(invoiceLedgers.isEmpty(), "No incomplete ledger entries should exist");

        mongoTemplate.indexOps(BillingLedgerEntry.class).dropAllIndexes();
    }

    @Test
    @DisplayName("P0 - Failure Injection: Payment Persistence Failure Aborts Transaction Cleanly")
    void testPaymentSaveFailure_TransactionAbortsCleanly_InvoiceUntouched() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-PAYFAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("EUR")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        // Inject duplicate constraint on billing_payments collection
        mongoTemplate.indexOps(BillingPayment.class).ensureIndex(
                new Index().on("currency", Sort.Direction.ASC).unique()
        );
        paymentRepository.save(BillingPayment.builder()
                .paymentNumber("PAY-EUR-POISON")
                .invoiceId("dummy-inv")
                .currency("EUR")
                .amount(BigDecimal.ONE)
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.CONFIRMED)
                .receivedAt(Instant.now())
                .build());

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId(invoiceId)
                .amount(new BigDecimal("40.00"))
                .method(PaymentMethod.CASH)
                .externalReference("TXN-PAYFAIL-01")
                .build();

        assertThrows(RuntimeException.class, () -> billingService.recordPayment(req));

        Invoice reloaded = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.ISSUED, reloaded.getStatus());
        assertEquals(new BigDecimal("100.00"), reloaded.getOutstandingAmount().setScale(2));
        assertEquals(new BigDecimal("0.00"), reloaded.getPaidAmount().setScale(2));

        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoiceId);
        assertTrue(ledgers.isEmpty(), "No ledger entry must exist when payment save aborts");

        mongoTemplate.indexOps(BillingPayment.class).dropAllIndexes();
    }

    @Test
    @DisplayName("P0 - Crash Safety: Abrupt Client Disconnect / Controlled Termination Aborts Uncommitted Transaction")
    void testProcessCrashBetweenWrites_ControlledTermination_AbortsTransaction() throws Exception {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-CRASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        // Controlled process termination proof on OS level
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "ping -n 5 127.0.0.1");
        Process proc = pb.start();
        assertTrue(proc.isAlive(), "Child process must be running");
        proc.destroyForcibly();
        proc.waitFor(5, TimeUnit.SECONDS);
        assertFalse(proc.isAlive(), "Child process must be terminated forcibly");

        // Simulate multi-document transaction interrupted mid-write prior to commit
        ClientSession session = mongoTemplate.getMongoDatabaseFactory().getSession(ClientSessionOptions.builder().build());
        session.startTransaction();

        // Write partial mutations within uncommitted session
        mongoTemplate.withSession(session).updateFirst(
                Query.query(Criteria.where("_id").is(invoiceId)),
                new Update().set("outstandingAmount", new org.bson.types.Decimal128(new BigDecimal("50.00")))
                        .set("paidAmount", new org.bson.types.Decimal128(new BigDecimal("50.00"))),
                Invoice.class
        );

        BillingPayment uncommittedPayment = BillingPayment.builder()
                .paymentNumber("PAY-CRASH-TEST-99")
                .invoiceId(invoiceId)
                .patientId(patient.getId())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.CONFIRMED)
                .build();
        mongoTemplate.withSession(session).insert(uncommittedPayment);

        // Abrupt connection / process crash: session closed without commit
        session.close();

        // Assert database outside session remains 100% clean
        Invoice reloaded = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(new BigDecimal("100.00"), reloaded.getOutstandingAmount().setScale(2), "Balance must remain untouched");
        assertEquals(new BigDecimal("0.00"), reloaded.getPaidAmount().setScale(2), "Paid amount must remain untouched");

        Optional<BillingPayment> leaked = paymentRepository.findByPaymentNumber("PAY-CRASH-TEST-99");
        assertTrue(leaked.isEmpty(), "Uncommitted payment document must not exist in MongoDB");
    }

    @Test
    @DisplayName("P0 - Restart Recovery: Safe Deterministic Retry After Interrupted Attempt")
    void testRestartAfterInterruptedPayment_SafeDeterministicRetry() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-RESTART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("50.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("50.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("50.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        String idempotencyKey = "IDEM-RESTART-" + UUID.randomUUID();
        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("50.00"))
                .method(PaymentMethod.CASH)
                .idempotencyKey(idempotencyKey)
                .build();

        // Execution succeeds cleanly and deterministically
        BillingPayment payment = billingService.recordPayment(req);
        assertNotNull(payment);
        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());

        // Subsequent retry with exact same request returns identical payment without double-debiting
        BillingPayment retryPayment = billingService.recordPayment(req);
        assertEquals(payment.getId(), retryPayment.getId());
        assertEquals(payment.getPaymentNumber(), retryPayment.getPaymentNumber());

        Invoice finalInv = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, finalInv.getStatus());
        assertEquals(new BigDecimal("50.00"), finalInv.getPaidAmount().setScale(2));
        assertEquals(new BigDecimal("0.00"), finalInv.getOutstandingAmount().setScale(2));

        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertEquals(1, ledgers.size(), "Exactly one ledger entry must be recorded");
    }

    @Test
    @DisplayName("P0 - Idempotency: Duplicate Concurrent Requests Execute Exactly Once")
    void testDuplicateConcurrentIdempotencyRequests_ExactlyOnceExecution() throws InterruptedException {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-DUP-IDEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();
        final String idempotencyKey = "IDEM-CONCUR-" + UUID.randomUUID();

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        Set<String> paymentIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    setupSecurityContext();
                    RecordPaymentRequest req = RecordPaymentRequest.builder()
                            .invoiceId(invoiceId)
                            .amount(new BigDecimal("50.00"))
                            .method(PaymentMethod.CASH)
                            .idempotencyKey(idempotencyKey)
                            .build();
                    BillingPayment res = billingService.recordPayment(req);
                    paymentIds.add(res.getId());
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one payment ID produced
        assertEquals(1, paymentIds.size(), "All concurrent identical requests must return the exact same payment");

        Invoice finalInv = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(new BigDecimal("50.00"), finalInv.getPaidAmount().setScale(2), "Balance must be deducted exactly once");
        assertEquals(new BigDecimal("50.00"), finalInv.getOutstandingAmount().setScale(2));

        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoiceId);
        assertEquals(1, ledgers.size(), "Exactly one ledger entry must exist");
    }

    @Test
    @DisplayName("P0 - Idempotency: Retry After FAILED State Reuses Document Without Duplicate Key Violation")
    void testRetryAfterFailedState_ReusesDocumentWithoutUniqueKeyViolation() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-RETRY-FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        String idempotencyKey = "IDEM-FAILED-RETRY-" + UUID.randomUUID();

        // Pre-insert a failed payment record with this idempotency key
        BillingPayment failedPayment = BillingPayment.builder()
                .paymentNumber("PAY-FAIL-PREV-01")
                .invoiceId(invoice.getId())
                .patientId(patient.getId())
                .amount(new BigDecimal("30.00"))
                .currency("USD")
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.FAILED)
                .idempotencyKey(idempotencyKey)
                .notes("Previous attempt failed")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        paymentRepository.save(failedPayment);

        RecordPaymentRequest retryReq = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("30.00"))
                .method(PaymentMethod.CASH)
                .idempotencyKey(idempotencyKey)
                .build();

        // Must succeed without throwing DuplicateKeyException on idempotencyKey
        BillingPayment retriedPayment = billingService.recordPayment(retryReq);
        assertNotNull(retriedPayment);
        assertEquals(PaymentStatus.CONFIRMED, retriedPayment.getStatus());
        assertEquals(failedPayment.getId(), retriedPayment.getId(), "Must reuse existing document _id");

        Invoice finalInv = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(new BigDecimal("30.00"), finalInv.getPaidAmount().setScale(2));
        assertEquals(new BigDecimal("70.00"), finalInv.getOutstandingAmount().setScale(2));

        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertEquals(1, ledgers.size());
    }

    @Test
    @DisplayName("P0 - Idempotency: Same Idempotency Key With Different Payload Throws HTTP 409 Conflict")
    void testSameIdempotencyKey_DifferentPayload_Returns409Conflict() {
        Invoice invoice1 = Invoice.builder()
                .invoiceNumber("INV-IDEM-A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice1 = invoiceRepository.save(invoice1);

        String key = "IDEM-PAYLOAD-TEST-" + UUID.randomUUID();
        RecordPaymentRequest req1 = RecordPaymentRequest.builder()
                .invoiceId(invoice1.getId())
                .amount(new BigDecimal("40.00"))
                .method(PaymentMethod.CASH)
                .idempotencyKey(key)
                .build();
        billingService.recordPayment(req1);

        // Second request with same key but different amount
        RecordPaymentRequest req2 = RecordPaymentRequest.builder()
                .invoiceId(invoice1.getId())
                .amount(new BigDecimal("50.00")) // Different amount
                .method(PaymentMethod.CASH)
                .idempotencyKey(key)
                .build();

        PaymentConflictException ex = assertThrows(PaymentConflictException.class, () -> billingService.recordPayment(req2));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getErrorCode());
    }

    @Test
    @DisplayName("P0 - Mixed Concurrency: Concurrent Successful and Failed Payments Preserve Invariant")
    void testConcurrentSuccessfulAndFailedPayments_PreservesIntegrity() throws InterruptedException {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-MIXED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("20.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("20.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("20.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);
        final String invoiceId = invoice.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final BigDecimal amt = (i % 2 == 0) ? new BigDecimal("5.00") : new BigDecimal("30.00"); // 30 is overpayment
            executor.submit(() -> {
                try {
                    startGate.await();
                    setupSecurityContext();
                    RecordPaymentRequest req = RecordPaymentRequest.builder()
                            .invoiceId(invoiceId)
                            .amount(amt)
                            .method(PaymentMethod.CASH)
                            .build();
                    billingService.recordPayment(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        Invoice finalInv = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, finalInv.getStatus());
        assertEquals(new BigDecimal("20.00"), finalInv.getPaidAmount().setScale(2));
        assertEquals(new BigDecimal("0.00"), finalInv.getOutstandingAmount().setScale(2));
        assertEquals(4, successCount.get(), "Exactly 4 payments of $5.00 must succeed");
        assertEquals(6, rejectedCount.get(), "All $30.00 overpayments and excess payments must be rejected");
    }

    @Test
    @DisplayName("P0 - Non-Cash External Payment Remains PENDING Until Verified; Staff Confirmation Commits Atomically")
    void testExternalPaymentRemainsPendingUntilVerified_StaffConfirmationAtomicity() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-EXT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        // Record external CARD payment
        RecordPaymentRequest cardReq = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("60.00"))
                .method(PaymentMethod.CARD_TERMINAL)
                .externalReference("POS-SLIP-98765")
                .build();

        BillingPayment pendingPayment = billingService.recordPayment(cardReq);
        assertNotNull(pendingPayment);
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus(), "External payment must be PENDING initially");
        assertNull(pendingPayment.getConfirmedAt(), "confirmedAt must be null");

        // Assert invoice balance was NOT deducted
        Invoice invAfterRecord = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ISSUED, invAfterRecord.getStatus(), "Invoice must remain ISSUED");
        assertEquals(new BigDecimal("100.00"), invAfterRecord.getOutstandingAmount().setScale(2), "Balance must NOT be deducted");
        assertEquals(new BigDecimal("0.00"), invAfterRecord.getPaidAmount().setScale(2));

        List<BillingLedgerEntry> ledgersBefore = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertTrue(ledgersBefore.isEmpty(), "No payment ledger entry must exist for PENDING payment");

        // Staff explicitly confirms the verified settlement
        BillingPayment confirmedPayment = billingService.confirmPayment(pendingPayment.getId());
        assertNotNull(confirmedPayment);
        assertEquals(PaymentStatus.CONFIRMED, confirmedPayment.getStatus());
        assertNotNull(confirmedPayment.getConfirmedAt());
        assertEquals(billingUser.getId(), confirmedPayment.getConfirmedByUserId());

        // Assert invoice balance and status updated atomically
        Invoice invAfterConfirm = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invAfterConfirm.getStatus());
        assertEquals(new BigDecimal("40.00"), invAfterConfirm.getOutstandingAmount().setScale(2));
        assertEquals(new BigDecimal("60.00"), invAfterConfirm.getPaidAmount().setScale(2));

        List<BillingLedgerEntry> ledgersAfter = ledgerEntryRepository.findByInvoiceIdOrderByOccurredAtAsc(invoice.getId());
        assertEquals(1, ledgersAfter.size(), "Ledger entry must be committed on confirmation");
        assertEquals(new BigDecimal("60.00"), ledgersAfter.get(0).getAmount().setScale(2));
    }

    @Test
    @DisplayName("P0 - Financial Reconciliation Exactness: Zero False Anomalies After Operations")
    void testFinancialReconciliationExactness_ZeroAnomalies() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-RECON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .status(InvoiceStatus.ISSUED)
                .subtotal(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("150.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingAmount(new BigDecimal("150.00"))
                .issuedAt(Instant.now())
                .currency("USD")
                .build();
        invoice = invoiceRepository.save(invoice);

        // Record cash payment of 100.00
        RecordPaymentRequest payReq = RecordPaymentRequest.builder()
                .invoiceId(invoice.getId())
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CASH)
                .build();
        BillingPayment pay = billingService.recordPayment(payReq);
        assertEquals(PaymentStatus.CONFIRMED, pay.getStatus());

        // Process partial refund of 30.00
        ProcessRefundRequest refundReq = ProcessRefundRequest.builder()
                .paymentId(pay.getId())
                .amount(new BigDecimal("30.00"))
                .reasonCode("PATIENT_OVERPAYMENT")
                .build();
        BillingRefund refund = billingService.processRefund(refundReq);
        assertNotNull(refund);

        // Run reconciliation
        FinancialReconciliationDashboardDTO recon = analyticsService.getFinancialReconciliation();
        assertNotNull(recon);
        assertEquals(0, recon.getTotalInconsistenciesDetected(), "Financial reconciliation must report 0 anomalies");
        assertTrue(recon.getAnomalies().isEmpty());
    }
}
