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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class FinancialReconciliationMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_reconciliation_" + UUID.randomUUID().toString().replace("-", "");

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
        } else if (System.getenv("MONGODB_URI") != null) {
            registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_URI"));
        }
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
    private Patient patient;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create and authenticate Admin User
        adminUser = User.builder()
                .id("admin-rec-" + UUID.randomUUID())
                .fullName("Admin Reconciler")
                .email("admin-rec-" + UUID.randomUUID() + "@pulsecare.com")
                .role(Role.ADMIN)
                .build();
        userRepository.save(adminUser);

        org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminUser.getEmail(), null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        context.setAuthentication(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        // 2. Create Patient
        patient = Patient.builder()
                .userId(adminUser.getId())
                .fullName("John Doe")
                .email("john.rec@example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        patient = patientRepository.save(patient);

        // 3. Create Sample Invoice
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

    @Test
    @DisplayName("P1: Normal BillingService recordPayment creates payment + ledger; getFinancialReconciliation must NOT flag it as missing")
    void testConfirmedPayment_WithNormalBillingService_NotClassifiedAsMissing() {
        RecordPaymentRequest request = RecordPaymentRequest.builder()
                .invoiceId(testInvoice.getId())
                .amount(new BigDecimal("2000.00"))
                .method(PaymentMethod.CASH)
                .idempotencyKey("IDEM-REC-VALID-01")
                .build();

        // Persist using normal BillingService implementation
        BillingPayment savedPayment = billingService.recordPayment(request);
        assertNotNull(savedPayment);
        assertNotNull(savedPayment.getId());
        assertEquals(PaymentStatus.CONFIRMED, savedPayment.getStatus());

        // Verify ledger entry was created
        List<BillingLedgerEntry> ledgers = ledgerEntryRepository.findAll();
        assertFalse(ledgers.isEmpty());
        assertEquals(savedPayment.getId(), ledgers.get(0).getPaymentId());

        // Run Financial Reconciliation Audit
        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);
        assertEquals(0, report.getTotalInconsistenciesDetected(),
                "Confirmed payment with valid ledger entry should NOT be classified as missing a ledger entry");
        assertTrue(report.getAnomalies().isEmpty());
    }

    @Test
    @DisplayName("P2: Confirmed payment without ledger entry is accurately detected by getFinancialReconciliation")
    void testConfirmedPayment_WithoutLedgerEntry_DetectedAsMissing() {
        // Create an unledgered payment directly in MongoDB
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

        // Run Financial Reconciliation Audit
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
    @DisplayName("P3: Both BSON ObjectId and String payment IDs are correctly resolved in reconciliation lookup")
    void testBothBsonObjectIdAndStringIds_Supported() {
        // 1. Insert valid payment with BSON ObjectId as _id
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

        // Matching ledger entry storing hex string of the ObjectId
        Document ledgerDoc1 = new Document("_id", new ObjectId())
                .append("entryNumber", "LEDG-OBJ-001")
                .append("invoiceId", testInvoice.getId())
                .append("paymentId", objId.toHexString())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("1000.00")))
                .append("entryType", "PAYMENT")
                .append("occurredAt", Instant.now());
        mongoTemplate.getCollection("billing_ledger_entries").insertOne(ledgerDoc1);

        // 2. Insert valid payment with String as _id
        String stringId = "LEGACY-PAY-STRING-001";
        Document paymentDocWithStringId = new Document("_id", stringId)
                .append("paymentNumber", "PAY-STR-001")
                .append("invoiceId", testInvoice.getId())
                .append("patientId", patient.getId())
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("2000.00")))
                .append("refundedAmount", new org.bson.types.Decimal128(BigDecimal.ZERO))
                .append("status", "CONFIRMED")
                .append("method", "CARD")
                .append("createdAt", Instant.now())
                .append("confirmedAt", Instant.now());
        mongoTemplate.getCollection("billing_payments").insertOne(paymentDocWithStringId);

        // Matching ledger entry storing the exact string ID
        Document ledgerDoc2 = new Document("_id", new ObjectId())
                .append("entryNumber", "LEDG-STR-002")
                .append("invoiceId", testInvoice.getId())
                .append("paymentId", stringId)
                .append("amount", new org.bson.types.Decimal128(new BigDecimal("2000.00")))
                .append("entryType", "PAYMENT")
                .append("occurredAt", Instant.now());
        mongoTemplate.getCollection("billing_ledger_entries").insertOne(ledgerDoc2);

        // 3. Insert an orphan payment with String ID (no ledger entry)
        Document orphanDoc = new Document("_id", "LEGACY-ORPHAN-003")
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

        // Run reconciliation
        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();
        assertNotNull(report);

        // Exactly the orphan payment should be detected
        assertEquals(1, report.getTotalInconsistenciesDetected(),
                "Only the unledgered payment should be flagged; valid ObjectId and String IDs must match");
        assertEquals("LEGACY-ORPHAN-003", report.getAnomalies().get(0).getEntityId());
        assertEquals("PAY-ORPHAN-STR-003", report.getAnomalies().get(0).getReferenceNumber());
    }
}
