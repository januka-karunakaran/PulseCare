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
import java.util.*;

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

        AggregationResults<Document> emptyRes = new AggregationResults<>(List.of(), new Document());
        lenient().when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class))).thenReturn(emptyRes);
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
    @DisplayName("E14: Reconciliation detects paid invoices with positive outstanding balances and sets completeness")
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
        when(mongoTemplate.count(any(Query.class), eq(Invoice.class))).thenReturn(1L);
        when(mongoTemplate.count(any(Query.class), eq(BillingPayment.class))).thenReturn(0L);

        AggregationResults<Document> emptyAggRes = new AggregationResults<>(List.of(), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("billing_payments"), eq(Document.class))).thenReturn(emptyAggRes);

        FinancialReconciliationDashboardDTO report = analyticsService.getFinancialReconciliation();

        assertNotNull(report);
        assertEquals(1, report.getTotalInconsistenciesDetected());
        assertEquals(1, report.getReturnedAnomaliesCount());
        assertFalse(report.isTruncated());
        assertEquals("PAID_STATUS_WITH_OUTSTANDING_BALANCE", report.getAnomalies().get(0).getAnomalyType());
        assertEquals(new BigDecimal("25.00"), report.getAnomalies().get(0).getActualAmount());
    }

    @Test
    @DisplayName("B1: Patient registration timestamp fallback distinguishes complete, partial, and unavailable")
    void testPatientRegistrationCompleteness() {
        when(authz.getCurrentUser()).thenReturn(adminUser);
        when(patientRepository.count()).thenReturn(100L);

        // Case 1: No patients have timestamps -> UNAVAILABLE
        when(mongoTemplate.count(argThat(q -> q != null && q.getQueryObject().containsKey("createdAt") && !q.getQueryObject().containsKey("$gte")), eq(Patient.class)))
                .thenReturn(0L);

        ExecutiveOverviewDTO overview = analyticsService.getExecutiveOverview(AnalyticsFilterRequest.builder().build());
        assertNotNull(overview);
        assertNull(overview.getNewlyRegisteredPatients());
        assertEquals("UNAVAILABLE", overview.getRegistrationTimestampStatus());

        // Case 2: Partial patients have timestamps -> PARTIAL_HISTORICAL_DATA
        when(mongoTemplate.count(argThat(q -> q != null && q.getQueryObject().containsKey("createdAt") && !q.getQueryObject().containsKey("$gte")), eq(Patient.class)))
                .thenReturn(40L);
        when(mongoTemplate.count(argThat(q -> q != null && q.getQueryObject().containsKey("createdAt") && q.getQueryObject().get("createdAt") instanceof Document d && d.containsKey("$gte")), eq(Patient.class)))
                .thenReturn(5L);

        overview = analyticsService.getExecutiveOverview(AnalyticsFilterRequest.builder().build());
        assertEquals(5L, overview.getNewlyRegisteredPatients());
        assertEquals("PARTIAL_HISTORICAL_DATA", overview.getRegistrationTimestampStatus());
    }

    @Test
    @DisplayName("C1: Consultations count combines completed appointments and finalized encounters deduplicated")
    void testConsultationsAggregation_Deduplication() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        // Appt: patient-1 has completed appt
        Document apptDoc = new Document("_id", "patient-1");
        AggregationResults<Document> apptRes = new AggregationResults<>(List.of(apptDoc), new Document());

        // Encounter: patient-1 AND patient-2 have signed encounters
        Document encDoc1 = new Document("_id", "patient-1");
        Document encDoc2 = new Document("_id", "patient-2");
        AggregationResults<Document> encRes = new AggregationResults<>(List.of(encDoc1, encDoc2), new Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("appointments"), eq(Document.class))).thenReturn(apptRes);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("clinical_encounters"), eq(Document.class))).thenReturn(encRes);

        // Patient resolution
        Patient p1 = Patient.builder().id("patient-1").fullName("Alice").build();
        Patient p2 = Patient.builder().id("patient-2").fullName("Bob").build();
        when(mongoTemplate.find(any(Query.class), eq(Patient.class))).thenReturn(List.of(p1, p2));

        ExecutiveOverviewDTO overview = analyticsService.getExecutiveOverview(AnalyticsFilterRequest.builder().build());
        assertNotNull(overview);
        // patient-1 appears in both sources, patient-2 in encounters -> total distinct should be exactly 2
        assertEquals(2L, overview.getActivePatientsPeriod());
    }

    @Test
    @DisplayName("D1: Pharmacy stock aggregates by unit preserving decimal precision and avoiding mixed-unit sum")
    void testPharmacyStockByUnit_DecimalPreserved() {
        when(authz.getCurrentUser()).thenReturn(adminUser);
        when(medicineRepository.count()).thenReturn(5L);

        // Unit aggregation returns TABLETS (100.0) and ML (25.5)
        Document tabDoc = new Document("_id", "TABLETS").append("totalUnitQuantity", 100.0);
        Document mlDoc = new Document("_id", "ML").append("totalUnitQuantity", 25.5);
        AggregationResults<Document> unitRes = new AggregationResults<>(List.of(tabDoc, mlDoc), new Document());

        // Distinct medicine aggregation returns 2
        Document medDoc1 = new Document("_id", "med-1");
        Document medDoc2 = new Document("_id", "med-2");
        AggregationResults<Document> distMedsRes = new AggregationResults<>(List.of(medDoc1, medDoc2), new Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("pharmacy_stock_batches"), eq(Document.class)))
                .thenReturn(unitRes)
                .thenReturn(distMedsRes);

        when(mongoTemplate.count(any(Query.class), eq(PharmacyStockBatch.class))).thenReturn(4L);

        PharmacyAnalyticsDTO dto = analyticsService.getPharmacyAnalytics(AnalyticsFilterRequest.builder().build());

        assertNotNull(dto);
        assertEquals(4L, dto.getAvailableBatchesCount());
        assertEquals(2L, dto.getDistinctMedicinesInStock());
        assertNotNull(dto.getStockByUnit());
        assertEquals(100.0, dto.getStockByUnit().get("TABLETS"));
        assertEquals(25.5, dto.getStockByUnit().get("ML"));
    }
}
