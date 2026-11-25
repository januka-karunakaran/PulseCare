package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.*;
import com.hospital.backend.enums.*;
import org.springframework.security.access.AccessDeniedException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.AnalyticsServiceImpl;
import org.bson.Document;
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
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AuthorizationService authz;

    @Mock
    private ClinicalAuditService auditService;

    @Mock
    private ReportExportService exportService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BillingPaymentRepository paymentRepository;

    @Mock
    private BillingRefundRepository refundRepository;

    @Mock
    private BillingLedgerEntryRepository ledgerEntryRepository;

    @Mock
    private PharmacyStockBatchRepository stockBatchRepository;

    @Mock
    private PharmacyMedicineRepository medicineRepository;

    @Mock
    private LabOrderRepository labOrderRepository;

    @Mock
    private LabResultRepository labResultRepository;

    @Mock
    private ClinicalEncounterRepository encounterRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private User adminUser;
    private User doctorUser;
    private User patientUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id("admin-1").email("admin@pulsecare.com").role(Role.ADMIN).build();
        doctorUser = User.builder().id("doc-user-1").email("doctor@pulsecare.com").role(Role.DOCTOR).build();
        patientUser = User.builder().id("pat-1").email("patient@pulsecare.com").role(Role.PATIENT).build();
    }

    @Test
    @DisplayName("A1-A3: Executive Overview returns accurate metrics for ADMIN with database-side aggregations")
    void testExecutiveOverview_Admin_Success() {
        when(authz.getCurrentUser()).thenReturn(adminUser);
        when(patientRepository.count()).thenReturn(100L);
        when(doctorRepository.count()).thenReturn(10L);
        when(mongoTemplate.count(any(Query.class), eq(Doctor.class))).thenReturn(8L);

        // Bed aggregation result
        Document bedDoc = new Document("total", 2L).append("occupied", 1L).append("available", 1L).append("maintenance", 0L);
        AggregationResults<Document> bedAggRes = new AggregationResults<>(List.of(bedDoc), new Document());

        // Appointment aggregation result
        Document apptDoc = new Document("total", 2L).append("completed", 1L).append("booked", 0L).append("cancelled", 1L).append("pending", 0L);
        AggregationResults<Document> apptAggRes = new AggregationResults<>(List.of(apptDoc), new Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("beds"), eq(Document.class))).thenReturn(bedAggRes);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("appointments"), eq(Document.class))).thenReturn(apptAggRes);

        AnalyticsFilterRequest req = AnalyticsFilterRequest.builder()
                .dateRange(ReportingDateRange.LAST_30_DAYS)
                .build();

        ExecutiveOverviewDTO overview = analyticsService.getExecutiveOverview(req);

        assertNotNull(overview);
        assertEquals(100L, overview.getTotalRegisteredPatients());
        assertEquals(10L, overview.getTotalDoctors());
        assertEquals(8L, overview.getActiveDoctors());
        assertEquals(2L, overview.getTotalBeds());
        assertEquals(1L, overview.getOccupiedBeds());
        assertEquals(50.0, overview.getBedOccupancyRate());
        assertEquals(2L, overview.getAppointmentsPeriod());
        assertEquals(1L, overview.getCompletedAppointmentsPeriod());
        assertEquals(50.0, overview.getAppointmentCompletionRate());
    }

    @Test
    @DisplayName("F1: Patient is denied access to Executive Overview")
    void testExecutiveOverview_Patient_AccessDenied() {
        when(authz.getCurrentUser()).thenReturn(patientUser);

        AnalyticsFilterRequest req = AnalyticsFilterRequest.builder().build();
        assertThrows(AccessDeniedException.class, () -> analyticsService.getExecutiveOverview(req));
    }

    @Test
    @DisplayName("A4: Doctor gets scoped appointment analytics")
    void testAppointmentAnalytics_DoctorScope() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(doctorRepository.findByUserId("doc-user-1")).thenReturn(Optional.of(
                Doctor.builder().id("doc-entity-1").userId("doc-user-1").fullName("Dr. Smith").build()
        ));

        Document apptDoc = new Document("total", 1L).append("completed", 1L).append("booked", 0L).append("cancelled", 0L).append("pending", 0L);
        AggregationResults<Document> apptAggRes = new AggregationResults<>(List.of(apptDoc), new Document());
        AggregationResults<Document> emptyAggRes = new AggregationResults<>(List.of(), new Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("appointments"), eq(Document.class)))
                .thenReturn(apptAggRes)
                .thenReturn(emptyAggRes)
                .thenReturn(emptyAggRes);

        AnalyticsFilterRequest req = AnalyticsFilterRequest.builder()
                .dateRange(ReportingDateRange.LAST_7_DAYS)
                .build();

        AppointmentAnalyticsDTO apptAnalytics = analyticsService.getAppointmentAnalytics(req);

        assertNotNull(apptAnalytics);
        assertEquals(1L, apptAnalytics.getTotalAppointments());
        assertEquals(1L, apptAnalytics.getCompletedAppointments());
        assertEquals(100.0, apptAnalytics.getCompletionRate());
    }

    @Test
    @DisplayName("E1-E8: Financial Analytics calculates exact BigDecimal totals")
    void testBillingAnalytics_ExactBigDecimalPrecision() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        Invoice inv1 = Invoice.builder()
                .id("inv-1")
                .totalAmount(new BigDecimal("150.00"))
                .discount(new BigDecimal("10.00"))
                .tax(new BigDecimal("5.00"))
                .outstandingAmount(new BigDecimal("50.00"))
                .status(InvoiceStatus.PARTIALLY_PAID)
                .issuedAt(Instant.now())
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Invoice.class))).thenReturn(List.of(inv1));

        BillingPayment pay1 = BillingPayment.builder()
                .id("pay-1")
                .amount(new BigDecimal("100.00"))
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .build();
        when(mongoTemplate.find(any(Query.class), eq(BillingPayment.class))).thenReturn(List.of(pay1));
        when(mongoTemplate.find(any(Query.class), eq(BillingRefund.class))).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(BillingCreditNote.class))).thenReturn(List.of());

        AggregationResults<Document> emptyAggRes = new AggregationResults<>(List.of(), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class))).thenReturn(emptyAggRes);
        when(invoiceRepository.findAll()).thenReturn(List.of(inv1));

        AnalyticsFilterRequest req = AnalyticsFilterRequest.builder()
                .dateRange(ReportingDateRange.THIS_MONTH)
                .build();

        BillingFinancialAnalyticsDTO billing = analyticsService.getBillingAnalytics(req);

        assertNotNull(billing);
        assertEquals(new BigDecimal("150.00"), billing.getGrossBilledAmount());
        assertEquals(new BigDecimal("10.00"), billing.getTotalDiscounts());
        assertEquals(new BigDecimal("5.00"), billing.getTotalTax());
        assertEquals(new BigDecimal("100.00"), billing.getConfirmedCollections());
        assertEquals(new BigDecimal("50.00"), billing.getOutstandingReceivables());
        assertEquals(new BigDecimal("0.00"), billing.getTotalRefunds());
    }

    @Test
    @DisplayName("E14: Reconciliation detects paid invoices with positive outstanding balances via bounded query")
    void testFinancialReconciliation_DetectsAnomaly() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        Invoice invalidInvoice = Invoice.builder()
                .id("inv-anomaly")
                .invoiceNumber("INV-ERR-001")
                .status(InvoiceStatus.PAID)
                .outstandingAmount(new BigDecimal("25.00"))
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Invoice.class))).thenReturn(List.of(invalidInvoice));
        when(mongoTemplate.find(any(Query.class), eq(BillingPayment.class))).thenReturn(List.of());

        AggregationResults<Document> emptyAggRes = new AggregationResults<>(List.of(), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("billing_payments"), eq(Document.class))).thenReturn(emptyAggRes);

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();

        assertNotNull(report);
        assertEquals(1, report.getTotalInconsistenciesDetected());
        assertEquals("PAID_STATUS_WITH_OUTSTANDING_BALANCE", report.getAnomalies().get(0).getAnomalyType());
        assertEquals(new BigDecimal("25.00"), report.getAnomalies().get(0).getActualAmount());
    }
}
