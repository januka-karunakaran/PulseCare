package com.hospital.backend.service;

import com.hospital.backend.dto.billing.RecordPaymentRequest;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.PaymentConflictException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.domain.Sort;
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
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User billingUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        chargeRepository.deleteAll();
        catalogRepository.deleteAll();
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
    }

    @Test
    @DisplayName("P0 - 20 Concurrent Threads Attempting $10 Payments on a $100 Outstanding Invoice")
    void testConcurrentPayments_ZeroOverpayment_ZeroNegativeBalance() throws InterruptedException {
        // Create an issued invoice with $100.00 outstanding balance
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-CONC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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

        // 20 concurrent threads trying to pay $10.00 each simultaneously
        // (Total attempted: $200.00, but only $100.00 is outstanding)
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
                    // Set up security context for thread
                    org.springframework.security.core.context.SecurityContext context =
                            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                    billingUser.getEmail(), null,
                                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_BILLING_STAFF"))
                            );
                    context.setAuthentication(auth);
                    org.springframework.security.core.context.SecurityContextHolder.setContext(context);

                    RecordPaymentRequest req = RecordPaymentRequest.builder()
                            .invoiceId(invoiceId)
                            .amount(new BigDecimal("10.00"))
                            .method(PaymentMethod.CASH)
                            .externalReference("TXN-" + index)
                            .build();

                    billingService.recordPayment(req);
                    successCount.incrementAndGet();
                } catch (PaymentConflictException | com.hospital.backend.exception.OverpaymentException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + index + " failed with: " + e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    conflictCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Release the latch to start all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within 30 seconds");

        // Verify results: exactly 10 payments should succeed ($100 / $10 = 10)
        assertEquals(10, successCount.get(), "Exactly 10 payments of $10.00 must succeed");
        assertEquals(10, conflictCount.get(), "Exactly 10 payments must be rejected due to conflict or overpayment");

        // Reload invoice from MongoDB
        Invoice finalInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(InvoiceStatus.PAID, finalInvoice.getStatus(), "Invoice must be marked PAID");
        assertEquals(new BigDecimal("100.00"), finalInvoice.getPaidAmount().setScale(2), "Total paid must be exactly 100.00");
        assertEquals(new BigDecimal("0.00"), finalInvoice.getOutstandingAmount().setScale(2), "Outstanding must be exactly 0.00");
        assertTrue(finalInvoice.getOutstandingAmount().compareTo(BigDecimal.ZERO) >= 0, "Outstanding balance must NEVER be negative");
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
}
