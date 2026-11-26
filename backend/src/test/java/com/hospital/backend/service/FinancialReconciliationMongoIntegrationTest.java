package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.FinancialReconciliationDashboardDTO;
import com.hospital.backend.dto.billing.RecordPaymentRequest;
import com.hospital.backend.enums.*;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mandatory Financial Reconciliation MongoDB Integration Test.
 * Enforces strict Docker isolation, disposable Testcontainers database,
 * and verifies type-safe BSON joins between payments and double-entry ledger entries.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class FinancialReconciliationMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_reconciliation_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
        registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
    }

    @BeforeAll
    public static void verifyIsolatedEnvironment() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            fail("FAIL-CLOSED: Docker is mandatory for FinancialReconciliationMongoIntegrationTest. Connecting to non-isolated environments is prohibited.");
        }
        assertTrue(mongoDBContainer.isRunning(),
                "FAIL-CLOSED: Disposable Testcontainers MongoDB instance must be running");
    }

    @Autowired
    private BillingService billingService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BillingPaymentRepository paymentRepository;

    @Autowired
    private BillingLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User adminUser;
    private User patientUser;
    private Patient patient;
    private Invoice testInvoice;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        // Enforce DB Isolation Guard: Ensure we NEVER touch any database other than our disposable test DB
        String connectedDb = mongoTemplate.getDb().getName();
        assertTrue(connectedDb.startsWith("disposable_reconciliation_"),
                "SECURITY GUARD: Refusing destructive operations on non-isolated database: " + connectedDb);
        assertEquals(ISOLATED_TEST_DB, connectedDb);
        assertTrue(mongoDBContainer.isRunning(),
                "SECURITY GUARD: MongoDB container must be active");

        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create distinct Admin User
        adminUser = User.builder()
                .id("admin-rec-" + UUID.randomUUID())
                .fullName("Admin Reconciler")
                .email("admin.rec." + UUID.randomUUID() + "@pulsecare.com")
                .role(Role.ADMIN)
                .build();
        userRepository.save(adminUser);

        // 2. Create distinct Patient User
        patientUser = User.builder()
                .id("patient-rec-" + UUID.randomUUID())
                .fullName("Alice Patient")
                .email("alice.rec." + UUID.randomUUID() + "@patient.com")
                .role(Role.PATIENT)
                .build();
        userRepository.save(patientUser);

        // Default test security context: authenticated as Admin
        authenticateAs(adminUser);

        // 3. Create Patient Domain Entity
        patient = Patient.builder()
                .id("pat-entity-" + UUID.randomUUID())
                .userId(patientUser.getId())
                .fullName("Alice Patient")
                .email(patientUser.getEmail())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        patient = patientRepository.save(patient);

        // 4. Create Sample Invoice
        testInvoice = Invoice.builder()
                .invoiceNumber("INV-REC-001")
                .patientId(patient.getId())
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("5000.00"))
                .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .outstandingAmount(new BigDecimal("5000.00"))
                .currency("LKR")
                .issuedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testInvoice = invoiceRepository.save(testInvoice);
    }

    private void authenticateAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("A: BillingService creates confirmed payment and ledger entry; reconciliation reports zero false anomalies")
    void testNormalBillingServicePayment_CreatesPaymentAndLedger_ZeroFalseAnomalies() {
        RecordPaymentRequest request = RecordPaymentRequest.builder()
                .invoiceId(testInvoice.getId())
                .amount(new BigDecimal("2000.00"))
                .method(PaymentMethod.CASH)
                .idempotencyKey("IDEM-REC-VALID-01")
                .build();

        // 1. Record payment through production BillingService
        BillingPayment savedPayment = billingService.recordPayment(request);
        assertNotNull(savedPayment);
        assertNotNull(savedPayment.getId());
        assertEquals(PaymentStatus.CONFIRMED, savedPayment.getStatus());

        // 2. Verify actual MongoDB BSON types directly via MongoCollection
        Document paymentDoc = mongoTemplate.getCollection("billing_payments")
                .find(new Document("paymentNumber", savedPayment.getPaymentNumber())).first();
        assertNotNull(paymentDoc);
        assertTrue(paymentDoc.get("_id") instanceof ObjectId,
                "BillingPayment._id must be persisted as BSON ObjectId");

        Document ledgerDoc = mongoTemplate.getCollection("billing_ledger_entries")
                .find(new Document("paymentId", savedPayment.getId())).first();
        assertNotNull(ledgerDoc);
        assertTrue(ledgerDoc.get("paymentId") instanceof String,
                "BillingLedgerEntry.paymentId must be persisted as BSON String");

        // 3. Execute financial reconciliation audit
        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(0, report.getTotalInconsistenciesDetected(),
                "Confirmed payment with corresponding ledger entry must NOT be flagged as missing");
        assertTrue(report.getAnomalies().isEmpty());
    }

    @Test
    @DisplayName("B: Confirmed payment without ledger entry is detected exactly once")
    void testConfirmedPayment_WithoutLedgerEntry_DetectedExactlyOnce() {
        BillingPayment orphanPayment = BillingPayment.builder()
                .paymentNumber("PAY-ORPHAN-001")
                .invoiceId(testInvoice.getId())
                .patientId(patient.getId())
                .amount(new BigDecimal("1500.00"))
                .refundedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .method(PaymentMethod.CARD_TERMINAL)
                .status(PaymentStatus.CONFIRMED)
                .currency("LKR")
                .confirmedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        orphanPayment = paymentRepository.save(orphanPayment);

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(1, report.getTotalInconsistenciesDetected(),
                "Exactly one inconsistency should be detected for the orphan confirmed payment");
        assertEquals(1, report.getAnomalies().size());

        FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem anomaly = report.getAnomalies().get(0);
        assertEquals("CONFIRMED_PAYMENT_MISSING_LEDGER_ENTRY", anomaly.getAnomalyType());
        assertEquals("PAYMENT", anomaly.getReferenceEntity());
        assertEquals(orphanPayment.getId(), anomaly.getEntityId());
        assertEquals("PAY-ORPHAN-001", anomaly.getReferenceNumber());
    }

    @Test
    @DisplayName("C: BSON ObjectId and legacy String ID representations both work symmetrically")
    void testBothBsonObjectIdAndLegacyStringIds_SupportedSymmetrically() {
        // 1. Payment with BSON ObjectId as _id + ledger with string paymentId
        ObjectId objId = new ObjectId();
        Document paymentDocWithObjectId = new Document("_id", objId)
                .append("paymentNumber", "PAY-OBJID-001")
                .append("invoiceId", testInvoice.getId())
                .append("patientId", patient.getId())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("1000.00")))
                .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .append("status", "CONFIRMED")
                .append("method", "CASH")
                .append("createdAt", Instant.now())
                .append("confirmedAt", Instant.now());
        mongoTemplate.getCollection("billing_payments").insertOne(paymentDocWithObjectId);

        Document ledgerDoc1 = new Document("_id", new ObjectId())
                .append("entryNumber", "LEDG-OBJ-001")
                .append("invoiceId", testInvoice.getId())
                .append("paymentId", objId.toHexString())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("1000.00")))
                .append("entryType", "PAYMENT")
                .append("occurredAt", Instant.now());
        mongoTemplate.getCollection("billing_ledger_entries").insertOne(ledgerDoc1);

        // 2. Legacy payment with String as _id + ledger with string paymentId
        String stringId = "LEGACY-PAY-STR-001";
        Document paymentDocWithStringId = new Document("_id", stringId)
                .append("paymentNumber", "PAY-STR-001")
                .append("invoiceId", testInvoice.getId())
                .append("patientId", patient.getId())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("2000.00")))
                .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .append("status", "CONFIRMED")
                .append("method", "CARD_TERMINAL")
                .append("createdAt", Instant.now())
                .append("confirmedAt", Instant.now());
        mongoTemplate.getCollection("billing_payments").insertOne(paymentDocWithStringId);

        Document ledgerDoc2 = new Document("_id", new ObjectId())
                .append("entryNumber", "LEDG-STR-002")
                .append("invoiceId", testInvoice.getId())
                .append("paymentId", stringId)
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("2000.00")))
                .append("entryType", "PAYMENT")
                .append("occurredAt", Instant.now());
        mongoTemplate.getCollection("billing_ledger_entries").insertOne(ledgerDoc2);

        // 3. Orphan payment with legacy String ID
        Document orphanDoc = new Document("_id", "LEGACY-ORPHAN-STR-003")
                .append("paymentNumber", "PAY-ORPHAN-STR-003")
                .append("invoiceId", testInvoice.getId())
                .append("patientId", patient.getId())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("500.00")))
                .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .append("status", "CONFIRMED")
                .append("method", "BANK_TRANSFER")
                .append("createdAt", Instant.now())
                .append("confirmedAt", Instant.now());
        mongoTemplate.getCollection("billing_payments").insertOne(orphanDoc);

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(1, report.getTotalInconsistenciesDetected(),
                "Only the unledgered String payment should be flagged; valid ObjectId and String IDs must match");
        assertEquals("LEGACY-ORPHAN-STR-003", report.getAnomalies().get(0).getEntityId());
    }

    @Test
    @DisplayName("D: Multiple payments with matching ledger entries do not produce false positives")
    void testMultiplePayments_WithMatchingLedgerEntries_NoFalsePositives() {
        for (int i = 1; i <= 5; i++) {
            ObjectId pid = new ObjectId();
            Document pDoc = new Document("_id", pid)
                    .append("paymentNumber", "PAY-MULTI-" + i)
                    .append("invoiceId", testInvoice.getId())
                    .append("patientId", patient.getId())
                    .append("amount", new org.bson.types.Decimal128(new BigDecimal("500.00")))
                    .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                    .append("status", "CONFIRMED")
                    .append("method", "CASH")
                    .append("createdAt", Instant.now())
                    .append("confirmedAt", Instant.now());
            mongoTemplate.getCollection("billing_payments").insertOne(pDoc);

            Document lDoc = new Document("_id", new ObjectId())
                    .append("entryNumber", "LEDG-MULTI-" + i)
                    .append("invoiceId", testInvoice.getId())
                    .append("paymentId", pid.toHexString())
                    .append("amount", new org.bson.types.Decimal128(new BigDecimal("500.00")))
                    .append("entryType", "PAYMENT")
                    .append("occurredAt", Instant.now());
            mongoTemplate.getCollection("billing_ledger_entries").insertOne(lDoc);
        }

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(0, report.getTotalInconsistenciesDetected(),
                "All 5 matched payments must resolve with zero anomalies");
    }

    @Test
    @DisplayName("E: Incorrect ledger/payment relationships are not accepted as valid")
    void testMismatchedLedgerPaymentId_IsDetectedAsAnomaly() {
        // Payment P1
        ObjectId p1Id = new ObjectId();
        Document p1Doc = new Document("_id", p1Id)
                .append("paymentNumber", "PAY-MISMATCH-1")
                .append("invoiceId", testInvoice.getId())
                .append("patientId", patient.getId())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("1000.00")))
                .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .append("status", "CONFIRMED")
                .append("method", "CASH")
                .append("createdAt", Instant.now())
                .append("confirmedAt", Instant.now());
        mongoTemplate.getCollection("billing_payments").insertOne(p1Doc);

        // Ledger entry pointing to non-existent payment P-DIFFERENT
        Document lDoc = new Document("_id", new ObjectId())
                .append("entryNumber", "LEDG-MISMATCH-1")
                .append("invoiceId", testInvoice.getId())
                .append("paymentId", new ObjectId().toHexString())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("1000.00")))
                .append("entryType", "PAYMENT")
                .append("occurredAt", Instant.now());
        mongoTemplate.getCollection("billing_ledger_entries").insertOne(lDoc);

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(1, report.getTotalInconsistenciesDetected(),
                "Payment with mismatched ledger must be flagged as missing its ledger entry");
    }

    @Test
    @DisplayName("F: Financial authorization and read-only behavior are preserved")
    void testAuthorization_AndReadOnlyBehavior() {
        // 1. Verify unauthorized role is rejected
        authenticateAs(patientUser);
        assertThrows(AccessDeniedException.class, () -> analyticsService.getFinancialReconciliation(),
                "PATIENT role must be denied access to financial reconciliation");

        // 2. Switch to admin and run reconciliation
        authenticateAs(adminUser);
        long initialInvoices = invoiceRepository.count();
        long initialPayments = paymentRepository.count();
        long initialLedgers = ledgerEntryRepository.count();

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);

        // 3. Verify zero database mutations occurred (read-only audit)
        assertEquals(initialInvoices, invoiceRepository.count());
        assertEquals(initialPayments, paymentRepository.count());
        assertEquals(initialLedgers, ledgerEntryRepository.count());
    }

    @Test
    @DisplayName("G: Database isolation guard verifies unique disposable database")
    void testIsolationGuard_UniqueDisposableDatabase() {
        String dbName = mongoTemplate.getDb().getName();
        assertTrue(dbName.startsWith("disposable_reconciliation_"));
        assertEquals(ISOLATED_TEST_DB, dbName);
    }
}
