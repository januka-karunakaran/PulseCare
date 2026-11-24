package com.hospital.backend.service;

import com.hospital.backend.dto.billing.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.*;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.BillingServiceImpl;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock
    private BillingServiceCatalogRepository catalogRepository;
    @Mock
    private BillingChargeRepository chargeRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BillingPaymentRepository paymentRepository;
    @Mock
    private BillingLedgerEntryRepository ledgerEntryRepository;
    @Mock
    private BillingCreditNoteRepository creditNoteRepository;
    @Mock
    private BillingRefundRepository refundRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AuthorizationService authz;
    @Mock
    private ClinicalAuditService auditService;
    @Mock
    private BillingPdfService billingPdfService;

    @InjectMocks
    private BillingServiceImpl billingService;

    private User billingUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        billingUser = User.builder()
                .id("bill-user-1")
                .email("billing@hospital.com")
                .role(Role.BILLING_STAFF)
                .build();

        patient = Patient.builder()
                .id("pat-1")
                .userId("pat-user-1")
                .fullName("John Doe")
                .build();

        lenient().when(authz.canManageBillingCatalog()).thenReturn(true);
        lenient().when(authz.canManageBilling()).thenReturn(true);
        lenient().when(authz.canRecordPayments()).thenReturn(true);
        lenient().when(authz.canProcessRefunds()).thenReturn(true);
        lenient().when(authz.getCurrentUser()).thenReturn(billingUser);
    }

    @Test
    @DisplayName("Create service catalog item successfully")
    void testCreateCatalogService_Success() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        when(catalogRepository.findByServiceCode("CNS-001")).thenReturn(Optional.empty());
        when(catalogRepository.save(any(BillingServiceCatalog.class))).thenAnswer(i -> {
            BillingServiceCatalog cat = i.getArgument(0);
            cat.setId("cat-1");
            return cat;
        });

        CreateServiceRequest req = CreateServiceRequest.builder()
                .serviceCode("CNS-001")
                .serviceName("General Consultation")
                .category(ServiceCategory.CONSULTATION)
                .unitPrice(new BigDecimal("100.00"))
                .taxCategory("STANDARD")
                .build();

        BillingServiceCatalog created = billingService.createService(req);
        assertNotNull(created);
        assertEquals("CNS-001", created.getServiceCode());
        assertEquals(new BigDecimal("100.00"), created.getUnitPrice());
    }

    @Test
    @DisplayName("Create service catalog item with duplicate code throws DuplicateResourceException")
    void testCreateCatalogService_DuplicateCode_ThrowsDuplicate() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        BillingServiceCatalog existing = BillingServiceCatalog.builder()
                .id("cat-existing")
                .serviceCode("CNS-001")
                .build();
        when(catalogRepository.findByServiceCode("CNS-001")).thenReturn(Optional.of(existing));

        CreateServiceRequest req = CreateServiceRequest.builder()
                .serviceCode("CNS-001")
                .serviceName("General Consultation")
                .category(ServiceCategory.CONSULTATION)
                .unitPrice(new BigDecimal("100.00"))
                .build();

        assertThrows(DuplicateResourceException.class, () -> billingService.createService(req));
    }

    @Test
    @DisplayName("Create manual charge successfully")
    void testCreateManualCharge_Success() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        when(patientRepository.findById("pat-1")).thenReturn(Optional.of(patient));
        BillingServiceCatalog catalog = BillingServiceCatalog.builder()
                .serviceCode("MISC-01")
                .serviceName("Special dressing")
                .unitPrice(new BigDecimal("25.00"))
                .build();
        when(catalogRepository.findByServiceCode("MISC-01")).thenReturn(Optional.of(catalog));
        when(chargeRepository.save(any(BillingCharge.class))).thenAnswer(i -> {
            BillingCharge c = i.getArgument(0);
            c.setId("chg-1");
            return c;
        });

        CreateManualChargeRequest req = CreateManualChargeRequest.builder()
                .patientId("pat-1")
                .serviceCode("MISC-01")
                .description("Special dressing")
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("25.00"))
                .reason("Clinically needed bandage")
                .build();

        BillingCharge charge = billingService.createManualCharge(req);
        assertNotNull(charge);
        assertEquals(new BigDecimal("50.00"), charge.getTotalAmount());
        assertEquals(ChargeStatus.PENDING, charge.getChargeStatus());
    }

    @Test
    @DisplayName("Create invoice draft from uninvoiced charges")
    void testCreateInvoiceDraft_Calculations() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        when(patientRepository.findById("pat-1")).thenReturn(Optional.of(patient));

        BillingCharge charge1 = BillingCharge.builder()
                .id("chg-1")
                .patientId("pat-1")
                .serviceCode("CNS-001")
                .descriptionSnapshot("Doctor visit")
                .quantity(new BigDecimal("1"))
                .unitPrice(new BigDecimal("100.00"))
                .lineSubtotal(new BigDecimal("100.00"))
                .taxSnapshot(new BigDecimal("5.00"))
                .totalAmount(new BigDecimal("105.00"))
                .chargeStatus(ChargeStatus.PENDING)
                .build();

        when(chargeRepository.findAllById(List.of("chg-1"))).thenReturn(List.of(charge1));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId("inv-1");
            return inv;
        });

        CreateInvoiceDraftRequest req = CreateInvoiceDraftRequest.builder()
                .patientId("pat-1")
                .chargeIds(List.of("chg-1"))
                .discountAmount(new BigDecimal("10.00"))
                .discountReason("Promotional discount")
                .build();

        Invoice inv = billingService.createInvoiceDraft(req);
        assertNotNull(inv);
        assertEquals(InvoiceStatus.DRAFT, inv.getStatus());
        assertEquals(new BigDecimal("100.00"), inv.getSubtotal());
        assertEquals(new BigDecimal("10.00"), inv.getDiscount());
        assertEquals(new BigDecimal("5.00"), inv.getTax());
        assertEquals(new BigDecimal("95.00"), inv.getTotalAmount());
        assertEquals(new BigDecimal("95.00"), inv.getOutstandingAmount());
        assertEquals(new BigDecimal("0.00"), inv.getPaidAmount());
    }

    @Test
    @DisplayName("Issue invoice transitions from DRAFT to ISSUED and registers ledger entry")
    void testIssueInvoice_Success() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        Invoice draft = Invoice.builder()
                .id("inv-1")
                .invoiceNumber("INV-2026-000001")
                .patientId("pat-1")
                .status(InvoiceStatus.DRAFT)
                .totalAmount(new BigDecimal("150.00"))
                .outstandingAmount(new BigDecimal("150.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(draft));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice issued = billingService.issueInvoice("inv-1");
        assertNotNull(issued);
        assertEquals(InvoiceStatus.ISSUED, issued.getStatus());
        assertNotNull(issued.getIssuedAt());
        verify(ledgerEntryRepository).save(any(BillingLedgerEntry.class));
        verify(outboxRepository).save(any(NotificationOutbox.class));
    }

    @Test
    @DisplayName("Record full payment marks invoice PAID atomically")
    void testRecordPayment_FullPayment() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        Invoice issued = Invoice.builder()
                .id("inv-1")
                .invoiceNumber("INV-2026-000001")
                .patientId("pat-1")
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("100.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(issued));
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Invoice.class)))
                .thenReturn(updateResult);

        Invoice updatedInvoice = Invoice.builder()
                .id("inv-1")
                .invoiceNumber("INV-2026-000001")
                .patientId("pat-1")
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("100.00"))
                .outstandingAmount(new BigDecimal("0.00"))
                .paidAmount(new BigDecimal("100.00"))
                .build();
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(issued)).thenReturn(Optional.of(updatedInvoice));

        when(paymentRepository.save(any(BillingPayment.class))).thenAnswer(i -> {
            BillingPayment p = i.getArgument(0);
            p.setId("pay-1");
            return p;
        });

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId("inv-1")
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CASH)
                .externalReference("CASH-001")
                .build();

        BillingPayment payment = billingService.recordPayment(req);
        assertNotNull(payment);
        assertEquals(new BigDecimal("100.00"), payment.getAmount());
        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
        verify(ledgerEntryRepository).save(any(BillingLedgerEntry.class));
    }

    @Test
    @DisplayName("Record overpayment throws OverpaymentException")
    void testRecordPayment_Overpayment_ThrowsException() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        Invoice issued = Invoice.builder()
                .id("inv-1")
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("100.00"))
                .outstandingAmount(new BigDecimal("50.00"))
                .paidAmount(new BigDecimal("50.00"))
                .build();

        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(issued));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId("inv-1")
                .amount(new BigDecimal("60.00")) // Exceeds outstanding balance of 50.00
                .method(PaymentMethod.CASH)
                .build();

        assertThrows(OverpaymentException.class, () -> billingService.recordPayment(req));
    }

    @Test
    @DisplayName("Record payment with concurrent race condition throws PaymentConflictException")
    void testRecordPayment_ConcurrencyConflict_ThrowsPaymentConflict() {
        when(authz.getCurrentUser()).thenReturn(billingUser);
        Invoice issued = Invoice.builder()
                .id("inv-1")
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("100.00"))
                .outstandingAmount(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(issued));
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(0L); // 0 records updated due to race condition
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Invoice.class)))
                .thenReturn(updateResult);

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .invoiceId("inv-1")
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CASH)
                .build();

        assertThrows(PaymentConflictException.class, () -> billingService.recordPayment(req));
    }

    @Test
    @DisplayName("Process refund restores invoice outstanding balance and logs ledger")
    void testProcessRefund_Success() {
        when(authz.getCurrentUser()).thenReturn(billingUser);

        BillingPayment payment = BillingPayment.builder()
                .id("pay-1")
                .paymentNumber("PAY-2026-000001")
                .invoiceId("inv-1")
                .patientId("pat-1")
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.CONFIRMED)
                .build();

        Invoice invoice = Invoice.builder()
                .id("inv-1")
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(new BigDecimal("100.00"))
                .outstandingAmount(BigDecimal.ZERO)
                .build();

        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(invoice));
        when(refundRepository.findByPaymentId("pay-1")).thenReturn(List.of());
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(BillingPayment.class))).thenReturn(updateResult);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Invoice.class))).thenReturn(updateResult);

        when(refundRepository.save(any(BillingRefund.class))).thenAnswer(i -> {
            BillingRefund r = i.getArgument(0);
            r.setId("ref-1");
            return r;
        });

        ProcessRefundRequest req = ProcessRefundRequest.builder()
                .paymentId("pay-1")
                .amount(new BigDecimal("40.00"))
                .reasonCode("BILLING_ERROR")
                .build();

        BillingRefund refund = billingService.processRefund(req);
        assertNotNull(refund);
        assertEquals(new BigDecimal("40.00"), refund.getAmount());
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Invoice.class));
        verify(ledgerEntryRepository).save(any(BillingLedgerEntry.class));
    }

    @Test
    @DisplayName("Void invoice marks VOID and cancels associated charges")
    void testVoidInvoice_Success() {
        when(authz.getCurrentUser()).thenReturn(billingUser);

        Invoice invoice = Invoice.builder()
                .id("inv-1")
                .status(InvoiceStatus.ISSUED)
                .paidAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("100.00"))
                .items(List.of(
                        Invoice.InvoiceItem.builder()
                                .chargeId("chg-1")
                                .build()
                ))
                .build();

        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        BillingCharge charge = BillingCharge.builder()
                .id("chg-1")
                .chargeStatus(ChargeStatus.INVOICED)
                .build();
        when(chargeRepository.findByInvoiceId("inv-1")).thenReturn(List.of(charge));

        Invoice voided = billingService.voidInvoice("inv-1", "Patient insurance error");
        assertNotNull(voided);
        assertEquals(InvoiceStatus.VOID, voided.getStatus());
        assertEquals(ChargeStatus.PENDING, charge.getChargeStatus());
        verify(chargeRepository).save(charge);
    }
}
