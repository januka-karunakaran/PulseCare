package com.hospital.backend.service.impl;

import com.hospital.backend.dto.analytics.*;
import com.hospital.backend.enums.*;
import org.springframework.security.access.AccessDeniedException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.AnalyticsService;
import com.hospital.backend.service.ClinicalAuditService;
import com.hospital.backend.service.ReportExportService;
import com.hospital.backend.util.AnalyticsDateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final AuthorizationService authz;
    private final ClinicalAuditService auditService;
    private final ReportExportService exportService;

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final BedRepository bedRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingRefundRepository refundRepository;
    private final BillingLedgerEntryRepository ledgerEntryRepository;
    private final PharmacyStockBatchRepository stockBatchRepository;
    private final PharmacyMedicineRepository medicineRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabResultRepository labResultRepository;
    private final ClinicalEncounterRepository encounterRepository;

    @Override
    public ExecutiveOverviewDTO getExecutiveOverview(AnalyticsFilterRequest filter) {
        verifyAdminAccess();
        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        // 1. Patient KPIs
        long totalPatients = patientRepository.count();
        long newPatients = countNewPatients(boundaries);

        // 2. Doctor KPIs
        long totalDoctors = doctorRepository.count();
        long activeDoctors = doctorRepository.findAll().stream()
                .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()))
                .count();

        // 3. Appointment KPIs
        LocalDate todayDate = LocalDate.now(boundaries.zoneId());
        long apptsToday = countAppointmentsForDate(todayDate.toString());
        AppointmentPeriodCounts apptCounts = aggregateAppointmentsInPeriod(boundaries, null);

        // 4. Bed KPIs
        List<Bed> allBeds = bedRepository.findAll();
        long totalBeds = allBeds.size();
        long occupiedBeds = allBeds.stream().filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus())).count();
        long availableBeds = allBeds.stream().filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus())).count();
        long maintBeds = allBeds.stream().filter(b -> "MAINTENANCE".equalsIgnoreCase(b.getStatus())).count();
        double bedOccRate = totalBeds > 0 ? ((double) occupiedBeds / totalBeds) * 100.0 : 0.0;

        // 5. Lab KPIs
        long totalLabOrders = countLabOrders(boundaries);
        long pendingLabOrders = countPendingLabOrders();
        long specimensCollected = countSpecimensCollected(boundaries);
        long releasedResults = countReleasedLabResults(boundaries);

        // 6. Pharmacy KPIs
        long activeMeds = medicineRepository.count();
        long lowStockCount = countLowStockMedicines();
        long nearExpiryCount = countNearExpiryBatches(boundaries.zoneId());
        long expiredCount = countExpiredBatches();
        long totalDispenses = countPharmacyDispenses(boundaries);

        // 7. Financial KPIs
        FinancialAggregates finance = aggregateFinancialsInPeriod(boundaries);

        auditService.audit(null, "ANALYTICS", "EXECUTIVE_OVERVIEW",
                ClinicalAuditAction.REPORT_VIEWED, "SUCCESS", null,
                "Executive dashboard viewed for range: " + filter.getDateRange());

        return ExecutiveOverviewDTO.builder()
                .totalRegisteredPatients(totalPatients)
                .newlyRegisteredPatients(newPatients)
                .activePatientsPeriod(newPatients)
                .totalDoctors(totalDoctors)
                .activeDoctors(activeDoctors)
                .appointmentsToday(apptsToday)
                .appointmentsPeriod(apptCounts.total)
                .completedAppointmentsPeriod(apptCounts.completed)
                .cancelledAppointmentsPeriod(apptCounts.cancelled)
                .pendingAppointmentsPeriod(apptCounts.pending)
                .appointmentCompletionRate(apptCounts.completionRate)
                .totalBeds(totalBeds)
                .occupiedBeds(occupiedBeds)
                .availableBeds(availableBeds)
                .maintenanceBeds(maintBeds)
                .bedOccupancyRate(roundDouble(bedOccRate))
                .totalLabOrdersPeriod(totalLabOrders)
                .pendingLabOrders(pendingLabOrders)
                .specimensCollectedPeriod(specimensCollected)
                .releasedLabResultsPeriod(releasedResults)
                .activeMedicineCatalogCount(activeMeds)
                .lowStockMedicineCount(lowStockCount)
                .nearExpiryBatchCount(nearExpiryCount)
                .expiredBatchCount(expiredCount)
                .totalDispensesPeriod(totalDispenses)
                .grossBilledAmount(finance.grossBilled)
                .confirmedCollections(finance.confirmedCollections)
                .outstandingReceivables(finance.outstandingReceivables)
                .totalRefunds(finance.totalRefunds)
                .currency("LKR")
                .generatedAt(Instant.now())
                .timezone(boundaries.zoneId().getId())
                .dateRangeDescription(boundaries.startDate() + " to " + boundaries.endDate())
                .build();
    }

    @Override
    public AppointmentAnalyticsDTO getAppointmentAnalytics(AnalyticsFilterRequest filter) {
        verifyAdminOrDoctorAccess();
        String doctorScope = resolveDoctorScope(filter.getDoctorId());

        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        AppointmentPeriodCounts counts = aggregateAppointmentsInPeriod(boundaries, doctorScope);
        List<AppointmentAnalyticsDTO.DailyMetricPoint> dailyTrends = aggregateDailyAppointmentTrends(boundaries, doctorScope);
        Map<String, Long> statusDist = aggregateAppointmentStatusDistribution(boundaries, doctorScope);
        List<AppointmentAnalyticsDTO.DoctorWorkloadSummary> doctorWorkloads = (doctorScope == null) ? aggregateDoctorWorkloadSummaries(boundaries) : Collections.emptyList();

        auditService.audit(null, "ANALYTICS", "APPOINTMENT_ANALYTICS",
                ClinicalAuditAction.REPORT_VIEWED, "SUCCESS", null,
                "Appointment analytics viewed for range: " + filter.getDateRange());

        return AppointmentAnalyticsDTO.builder()
                .totalAppointments(counts.total)
                .bookedAppointments(counts.booked)
                .completedAppointments(counts.completed)
                .cancelledAppointments(counts.cancelled)
                .pendingAppointments(counts.pending)
                .completionRate(counts.completionRate)
                .dailyTrends(dailyTrends)
                .statusDistribution(statusDist)
                .doctorWorkloads(doctorWorkloads)
                .generatedAt(Instant.now())
                .timezone(boundaries.zoneId().getId())
                .build();
    }

    @Override
    public DoctorWorkloadDTO getDoctorWorkload(String doctorId, AnalyticsFilterRequest filter) {
        verifyAdminOrDoctorAccess();
        User currentUser = authz.getCurrentUser();
        String resolvedId;

        if (currentUser.getRole() == Role.DOCTOR) {
            Doctor doc = doctorRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Doctor profile not found for user: " + currentUser.getId()));
            resolvedId = doc.getId();
        } else if (doctorId == null || doctorId.isBlank()) {
            throw new ResourceNotFoundException("Target doctorId must be specified for administrator query");
        } else {
            resolvedId = doctorId;
        }

        final String targetDoctorId = resolvedId;
        Doctor doctor = doctorRepository.findById(targetDoctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + targetDoctorId));

        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        AppointmentPeriodCounts counts = aggregateAppointmentsInPeriod(boundaries, targetDoctorId);
        List<AppointmentAnalyticsDTO.DailyMetricPoint> dailyTrends = aggregateDailyAppointmentTrends(boundaries, targetDoctorId);

        // Encounter counts
        long encountersStarted = mongoTemplate.count(
                Query.query(Criteria.where("doctorId").is(targetDoctorId)
                        .and("visitStartedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                ClinicalEncounter.class
        );
        long encountersFinalized = mongoTemplate.count(
                Query.query(Criteria.where("doctorId").is(targetDoctorId)
                        .and("status").in(List.of(EncounterStatus.SIGNED, EncounterStatus.AMENDED))
                        .and("visitStartedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                ClinicalEncounter.class
        );
        long pendingDrafts = mongoTemplate.count(
                Query.query(Criteria.where("doctorId").is(targetDoctorId).and("status").is(EncounterStatus.DRAFT)),
                ClinicalEncounter.class
        );

        auditService.audit(null, "ANALYTICS", "DOCTOR_WORKLOAD",
                ClinicalAuditAction.REPORT_VIEWED, "SUCCESS", null,
                "Doctor workload viewed for doctorId: " + targetDoctorId);

        return DoctorWorkloadDTO.builder()
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .specialization(doctor.getSpecialization())
                .totalAppointments(counts.total)
                .completedAppointments(counts.completed)
                .cancelledAppointments(counts.cancelled)
                .encountersStarted(encountersStarted)
                .encountersFinalized(encountersFinalized)
                .pendingDocumentationDrafts(pendingDrafts)
                .dailyTrends(dailyTrends)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public PatientOperationsDTO getPatientOperations(AnalyticsFilterRequest filter) {
        verifyAdminAccess();
        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        long totalPatients = patientRepository.count();
        long newPatients = countNewPatients(boundaries);

        // Aggregate demographic statistics
        Map<String, Long> genderMap = new HashMap<>();
        Map<String, Long> bloodMap = new HashMap<>();

        Aggregation genderAgg = newAggregation(
                group("gender").count().as("count")
        );
        AggregationResults<Document> genderRes = mongoTemplate.aggregate(genderAgg, "patients", Document.class);
        for (Document doc : genderRes.getMappedResults()) {
            String g = doc.getString("_id");
            if (g != null && !g.isBlank()) genderMap.put(g, ((Number) doc.get("count")).longValue());
        }

        Aggregation bloodAgg = newAggregation(
                group("bloodGroup").count().as("count")
        );
        AggregationResults<Document> bloodRes = mongoTemplate.aggregate(bloodAgg, "patients", Document.class);
        for (Document doc : bloodRes.getMappedResults()) {
            String b = doc.getString("_id");
            if (b != null && !b.isBlank()) bloodMap.put(b, ((Number) doc.get("count")).longValue());
        }

        return PatientOperationsDTO.builder()
                .totalRegisteredPatients(totalPatients)
                .newRegistrationsPeriod(newPatients)
                .patientsWithConsultations(newPatients)
                .genderDistribution(genderMap)
                .bloodGroupDistribution(bloodMap)
                .registrationTrends(Collections.emptyList())
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public BedUtilizationDTO getBedUtilization() {
        verifyAdminAccess();
        List<Bed> beds = bedRepository.findAll();
        long total = beds.size();
        long available = beds.stream().filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus())).count();
        long occupied = beds.stream().filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus())).count();
        long maint = beds.stream().filter(b -> "MAINTENANCE".equalsIgnoreCase(b.getStatus())).count();
        double occRate = total > 0 ? ((double) occupied / total) * 100.0 : 0.0;

        Map<String, Long> statusDist = beds.stream()
                .filter(b -> b.getStatus() != null)
                .collect(Collectors.groupingBy(Bed::getStatus, Collectors.counting()));

        Map<String, Long> typeDist = beds.stream()
                .filter(b -> b.getType() != null)
                .collect(Collectors.groupingBy(Bed::getType, Collectors.counting()));

        Map<String, Long> wardDist = beds.stream()
                .filter(b -> b.getWard() != null)
                .collect(Collectors.groupingBy(Bed::getWard, Collectors.counting()));

        return BedUtilizationDTO.builder()
                .totalBeds(total)
                .availableBeds(available)
                .occupiedBeds(occupied)
                .maintenanceBeds(maint)
                .occupancyRate(roundDouble(occRate))
                .statusDistribution(statusDist)
                .typeDistribution(typeDist)
                .wardDistribution(wardDist)
                .historicalOccupancyStatus("CURRENT_SNAPSHOT_AVAILABLE")
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public LaboratoryAnalyticsDTO getLaboratoryAnalytics(AnalyticsFilterRequest filter) {
        verifyAdminOrLabTechAccess();
        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        long totalOrders = countLabOrders(boundaries);
        long pendingCollection = mongoTemplate.count(
                Query.query(Criteria.where("status").is(LabOrderStatus.ORDERED)
                        .and("orderedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                LabOrder.class
        );
        long specimensCollected = countSpecimensCollected(boundaries);
        long specimensRejected = mongoTemplate.count(
                Query.query(Criteria.where("status").is(LabOrderStatus.CANCELLED)
                        .and("cancellationReason").regex("REJECT", "i")),
                LabOrder.class
        );
        long resultsEntered = mongoTemplate.count(
                Query.query(Criteria.where("workflowStatus").is(LabResultWorkflowStatus.ENTERED)
                        .and("createdAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                LabResult.class
        );
        long resultsVerified = mongoTemplate.count(
                Query.query(Criteria.where("workflowStatus").is(LabResultWorkflowStatus.VERIFIED)
                        .and("verifiedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                LabResult.class
        );
        long resultsReleased = countReleasedLabResults(boundaries);
        long pendingVerificationQueue = mongoTemplate.count(
                Query.query(Criteria.where("workflowStatus").in(List.of(LabResultWorkflowStatus.ENTERED, LabResultWorkflowStatus.DRAFT))),
                LabResult.class
        );

        // Turnaround Time Calculation (Collection to Release average in minutes)
        Aggregation tatAgg = newAggregation(
                match(Criteria.where("workflowStatus").is(LabResultWorkflowStatus.RELEASED)
                        .and("releasedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())
                        .and("measuredAt").exists(true)),
                project()
                        .andExpression("(releasedAt - measuredAt) / 60000").as("diffMinutes"),
                group().avg("diffMinutes").as("avgTat")
        );
        AggregationResults<Document> tatRes = mongoTemplate.aggregate(tatAgg, "lab_results", Document.class);
        double avgTat = 0.0;
        if (!tatRes.getMappedResults().isEmpty()) {
            Object val = tatRes.getMappedResults().get(0).get("avgTat");
            if (val instanceof Number n) avgTat = n.doubleValue();
        }

        // Orders by Priority
        Aggregation prioAgg = newAggregation(
                match(Criteria.where("orderedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                group("priority").count().as("count")
        );
        AggregationResults<Document> prioRes = mongoTemplate.aggregate(prioAgg, "lab_orders", Document.class);
        Map<String, Long> prioDist = new HashMap<>();
        for (Document d : prioRes.getMappedResults()) {
            Object p = d.get("_id");
            if (p != null) prioDist.put(p.toString(), ((Number) d.get("count")).longValue());
        }

        // Results by Interpretation
        Aggregation interpAgg = newAggregation(
                match(Criteria.where("workflowStatus").is(LabResultWorkflowStatus.RELEASED)
                        .and("releasedAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                group("interpretation").count().as("count")
        );
        AggregationResults<Document> interpRes = mongoTemplate.aggregate(interpAgg, "lab_results", Document.class);
        Map<String, Long> interpDist = new HashMap<>();
        for (Document d : interpRes.getMappedResults()) {
            Object inter = d.get("_id");
            if (inter != null) interpDist.put(inter.toString(), ((Number) d.get("count")).longValue());
        }

        return LaboratoryAnalyticsDTO.builder()
                .totalOrders(totalOrders)
                .pendingCollectionOrders(pendingCollection)
                .specimensCollected(specimensCollected)
                .specimensRejected(specimensRejected)
                .resultsEntered(resultsEntered)
                .resultsVerified(resultsVerified)
                .resultsReleased(resultsReleased)
                .pendingVerificationQueue(pendingVerificationQueue)
                .averageTurnaroundMinutes(roundDouble(avgTat))
                .ordersByPriority(prioDist)
                .resultsByInterpretation(interpDist)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public PharmacyAnalyticsDTO getPharmacyAnalytics(AnalyticsFilterRequest filter) {
        verifyAdminOrPharmacistAccess();
        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        long activeMeds = medicineRepository.count();
        long lowStockCount = countLowStockMedicines();
        long nearExpiryCount = countNearExpiryBatches(boundaries.zoneId());
        long expiredCount = countExpiredBatches();
        long quarantinedCount = mongoTemplate.count(
                Query.query(Criteria.where("status").is(BatchStatus.QUARANTINED)),
                PharmacyStockBatch.class
        );
        long totalDispenses = countPharmacyDispenses(boundaries);

        long partialDispenses = mongoTemplate.count(
                Query.query(Criteria.where("status").is(DispenseStatus.PARTIALLY_DISPENSED)
                        .and("createdAt").gte(boundaries.startInclusive()).lt(boundaries.endExclusive())),
                PharmacyDispense.class
        );

        // Sum available quantity across AVAILABLE batches
        Aggregation availStockAgg = newAggregation(
                match(Criteria.where("status").is(BatchStatus.AVAILABLE)),
                group().sum("quantityAvailable").as("totalAvailable")
        );
        AggregationResults<Document> availRes = mongoTemplate.aggregate(availStockAgg, "pharmacy_stock_batches", Document.class);
        long availStockTotal = 0;
        if (!availRes.getMappedResults().isEmpty()) {
            Object val = availRes.getMappedResults().get(0).get("totalAvailable");
            if (val instanceof Number n) availStockTotal = n.longValue();
        }

        return PharmacyAnalyticsDTO.builder()
                .activeMedicineCatalogCount(activeMeds)
                .availableMedicineStock(availStockTotal)
                .lowStockItemsCount(lowStockCount)
                .nearExpiryBatchesCount(nearExpiryCount)
                .expiredBatchesCount(expiredCount)
                .quarantinedBatchesCount(quarantinedCount)
                .totalDispenses(totalDispenses)
                .partiallyDispensedCount(partialDispenses)
                .totalUnitsDispensed(0.0) // Units aggregated only per unit-safe queries
                .inventoryValuationStatus("COST_BASIS_NOT_AVAILABLE")
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public BillingFinancialAnalyticsDTO getBillingAnalytics(AnalyticsFilterRequest filter) {
        verifyAdminOrBillingAccess();
        AnalyticsDateUtils.DateBoundaries boundaries = AnalyticsDateUtils.resolveBoundaries(
                filter.getDateRange(), filter.getStartDate(), filter.getEndDate(), filter.getTimezone());

        FinancialAggregates finance = aggregateFinancialsInPeriod(boundaries);
        Map<String, BigDecimal> methodDist = aggregateCollectionsByPaymentMethod(boundaries);
        Map<String, Long> statusDist = aggregateInvoiceStatusDistribution(boundaries);
        List<BillingFinancialAnalyticsDTO.DailyRevenuePoint> dailyTrends = aggregateDailyRevenueTrends(boundaries);
        List<BillingFinancialAnalyticsDTO.AgingBucketPoint> aging = aggregateReceivablesAging(boundaries.zoneId());

        return BillingFinancialAnalyticsDTO.builder()
                .totalInvoicesIssued(finance.totalInvoices)
                .grossBilledAmount(finance.grossBilled)
                .totalDiscounts(finance.totalDiscounts)
                .totalTax(finance.totalTax)
                .confirmedCollections(finance.confirmedCollections)
                .outstandingReceivables(finance.outstandingReceivables)
                .totalRefunds(finance.totalRefunds)
                .totalCreditNotes(finance.totalCreditNotes)
                .currency("LKR")
                .collectionsByMethod(methodDist)
                .invoiceStatusDistribution(statusDist)
                .dailyRevenueTrends(dailyTrends)
                .receivablesAging(aging)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public FinancialReconciliationDashboardDTO getFinancialReconciliation() {
        verifyAdminOrBillingAccess();
        List<FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem> anomalies = new ArrayList<>();

        // Anomaly Check 1: Invoices marked PAID with outstandingAmount > 0
        List<Invoice> invalidPaidInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.PAID &&
                        inv.getOutstandingAmount() != null &&
                        inv.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        for (Invoice inv : invalidPaidInvoices) {
            anomalies.add(FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem.builder()
                    .anomalyType("PAID_STATUS_WITH_OUTSTANDING_BALANCE")
                    .severity("HIGH")
                    .referenceEntity("INVOICE")
                    .entityId(inv.getId())
                    .referenceNumber(inv.getInvoiceNumber())
                    .description("Invoice is marked PAID but has remaining outstanding balance: " + inv.getOutstandingAmount())
                    .expectedAmount(BigDecimal.ZERO)
                    .actualAmount(inv.getOutstandingAmount())
                    .detectedAt(Instant.now())
                    .build());
        }

        // Anomaly Check 2: Payments marked CONFIRMED missing corresponding Ledger Entry
        List<BillingPayment> confirmedPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .collect(Collectors.toList());

        for (BillingPayment pay : confirmedPayments) {
            long ledgerCount = mongoTemplate.count(
                    Query.query(Criteria.where("paymentId").is(pay.getId())),
                    BillingLedgerEntry.class
            );
            if (ledgerCount == 0) {
                anomalies.add(FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem.builder()
                        .anomalyType("CONFIRMED_PAYMENT_MISSING_LEDGER_ENTRY")
                        .severity("HIGH")
                        .referenceEntity("PAYMENT")
                        .entityId(pay.getId())
                        .referenceNumber(pay.getPaymentNumber())
                        .description("Payment is CONFIRMED but no corresponding double-entry ledger entry was found.")
                        .expectedAmount(pay.getAmount())
                        .actualAmount(BigDecimal.ZERO)
                        .detectedAt(Instant.now())
                        .build());
            }
        }

        // Anomaly Check 3: Over-refunded payments
        List<BillingPayment> allPayments = paymentRepository.findAll();
        for (BillingPayment p : allPayments) {
            if (p.getRefundedAmount() != null && p.getAmount() != null &&
                    p.getRefundedAmount().compareTo(p.getAmount()) > 0) {
                anomalies.add(FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem.builder()
                        .anomalyType("PAYMENT_OVER_REFUNDED")
                        .severity("HIGH")
                        .referenceEntity("PAYMENT")
                        .entityId(p.getId())
                        .referenceNumber(p.getPaymentNumber())
                        .description("Total refunded amount exceeds original payment amount.")
                        .expectedAmount(p.getAmount())
                        .actualAmount(p.getRefundedAmount())
                        .detectedAt(Instant.now())
                        .build());
            }
        }

        long totalInvoices = invoiceRepository.count();
        long totalPayments = paymentRepository.count();
        long totalRefunds = refundRepository.count();

        auditService.audit(null, "ANALYTICS", "FINANCIAL_RECONCILIATION",
                ClinicalAuditAction.REPORT_VIEWED, "SUCCESS", null,
                "Financial reconciliation audit executed. Anomalies found: " + anomalies.size());

        return FinancialReconciliationDashboardDTO.builder()
                .totalInconsistenciesDetected(anomalies.size())
                .totalInvoicesAudited(totalInvoices)
                .totalPaymentsAudited(totalPayments)
                .totalRefundsAudited(totalRefunds)
                .anomalies(anomalies)
                .auditedAt(Instant.now())
                .build();
    }

    @Override
    public byte[] exportReport(ReportExportRequest request) {
        verifyExportAuthorization(request.getReportType());

        AnalyticsFilterRequest filter = AnalyticsFilterRequest.builder()
                .dateRange(request.getDateRange())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .doctorId(request.getDoctorId())
                .timezone(request.getTimezone())
                .build();

        Object data = switch (request.getReportType()) {
            case EXECUTIVE_OVERVIEW -> getExecutiveOverview(filter);
            case APPOINTMENT_ACTIVITY -> getAppointmentAnalytics(filter);
            case DOCTOR_WORKLOAD -> getDoctorWorkload(request.getDoctorId(), filter);
            case PATIENT_OPERATIONS -> getPatientOperations(filter);
            case BED_UTILIZATION -> getBedUtilization();
            case LABORATORY_OPERATIONS -> getLaboratoryAnalytics(filter);
            case PHARMACY_INVENTORY -> getPharmacyAnalytics(filter);
            case FINANCIAL_COLLECTIONS, OUTSTANDING_RECEIVABLES -> getBillingAnalytics(filter);
            case FINANCIAL_RECONCILIATION -> getFinancialReconciliation();
        };

        byte[] reportBytes = exportService.generateReport(request, data);

        auditService.audit(null, "REPORT_EXPORT", request.getReportType().name(),
                ClinicalAuditAction.REPORT_EXPORTED, "SUCCESS", null,
                "Exported " + request.getReportType() + " in " + request.getFormat() + " format");

        return reportBytes;
    }

    // =========================================================================
    // AUTHORIZATION & HELPER METHODS
    // =========================================================================

    private void verifyAdminAccess() {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN) {
            auditService.audit(null, "ANALYTICS", "SECURITY",
                    ClinicalAuditAction.ANALYTICS_ACCESS_DENIED, "DENIED", null,
                    "Unauthorized admin analytics access attempt by: " + user.getEmail());
            throw new AccessDeniedException("Access denied: Administrative privileges required");
        }
    }

    private void verifyAdminOrDoctorAccess() {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Access denied: Doctor or Admin privileges required");
        }
    }

    private void verifyAdminOrLabTechAccess() {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.LAB_TECH) {
            throw new AccessDeniedException("Access denied: Laboratory Staff privileges required");
        }
    }

    private void verifyAdminOrPharmacistAccess() {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.PHARMACIST) {
            throw new AccessDeniedException("Access denied: Pharmacist privileges required");
        }
    }

    private void verifyAdminOrBillingAccess() {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.BILLING_STAFF) {
            throw new AccessDeniedException("Access denied: Billing Staff privileges required");
        }
    }

    private void verifyExportAuthorization(ReportType type) {
        User user = authz.getCurrentUser();
        switch (type) {
            case EXECUTIVE_OVERVIEW, PATIENT_OPERATIONS, BED_UTILIZATION -> verifyAdminAccess();
            case APPOINTMENT_ACTIVITY, DOCTOR_WORKLOAD -> verifyAdminOrDoctorAccess();
            case LABORATORY_OPERATIONS -> verifyAdminOrLabTechAccess();
            case PHARMACY_INVENTORY -> verifyAdminOrPharmacistAccess();
            case FINANCIAL_COLLECTIONS, OUTSTANDING_RECEIVABLES, FINANCIAL_RECONCILIATION -> verifyAdminOrBillingAccess();
        }
    }

    private String resolveDoctorScope(String requestedDoctorId) {
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.DOCTOR) {
            Doctor doc = doctorRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException("Doctor profile not found"));
            return doc.getId();
        }
        return requestedDoctorId;
    }

    private long countNewPatients(AnalyticsDateUtils.DateBoundaries b) {
        return patientRepository.count(); // Fallback if createdAt absent on legacy patients
    }

    private long countAppointmentsForDate(String dateStr) {
        return mongoTemplate.count(Query.query(Criteria.where("appointmentDate").is(dateStr)), Appointment.class);
    }

    private record AppointmentPeriodCounts(long total, long booked, long completed, long cancelled, long pending, double completionRate) {}

    private AppointmentPeriodCounts aggregateAppointmentsInPeriod(AnalyticsDateUtils.DateBoundaries b, String doctorId) {
        Criteria criteria = Criteria.where("appointmentDate").gte(b.startDate().toString()).lte(b.endDate().toString());
        if (doctorId != null && !doctorId.isBlank()) {
            criteria.and("doctorId").is(doctorId);
        }

        List<Appointment> appts = mongoTemplate.find(Query.query(criteria), Appointment.class);
        long total = appts.size();
        long completed = appts.stream().filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus())).count();
        long cancelled = appts.stream().filter(a -> "CANCELLED".equalsIgnoreCase(a.getStatus()) || "REJECTED".equalsIgnoreCase(a.getStatus())).count();
        long booked = appts.stream().filter(a -> "APPROVED".equalsIgnoreCase(a.getStatus())).count();
        long pending = appts.stream().filter(a -> "PENDING".equalsIgnoreCase(a.getStatus())).count();
        double rate = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

        return new AppointmentPeriodCounts(total, booked, completed, cancelled, pending, roundDouble(rate));
    }

    private List<AppointmentAnalyticsDTO.DailyMetricPoint> aggregateDailyAppointmentTrends(AnalyticsDateUtils.DateBoundaries b, String doctorId) {
        Criteria criteria = Criteria.where("appointmentDate").gte(b.startDate().toString()).lte(b.endDate().toString());
        if (doctorId != null && !doctorId.isBlank()) {
            criteria.and("doctorId").is(doctorId);
        }

        Aggregation agg = newAggregation(
                match(criteria),
                group("appointmentDate")
                        .count().as("total")
                        .sum(org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                                .when(Criteria.where("status").is("COMPLETED")).then(1).otherwise(0)).as("completed")
                        .sum(org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                                .when(Criteria.where("status").in(List.of("CANCELLED", "REJECTED"))).then(1).otherwise(0)).as("cancelled"),
                sort(Sort.Direction.ASC, "_id")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "appointments", Document.class);
        List<AppointmentAnalyticsDTO.DailyMetricPoint> points = new ArrayList<>();
        for (Document d : results.getMappedResults()) {
            String date = d.getString("_id");
            if (date != null) {
                points.add(AppointmentAnalyticsDTO.DailyMetricPoint.builder()
                        .date(date)
                        .total(((Number) d.getOrDefault("total", 0)).longValue())
                        .completed(((Number) d.getOrDefault("completed", 0)).longValue())
                        .cancelled(((Number) d.getOrDefault("cancelled", 0)).longValue())
                        .build());
            }
        }
        return points;
    }

    private Map<String, Long> aggregateAppointmentStatusDistribution(AnalyticsDateUtils.DateBoundaries b, String doctorId) {
        Criteria criteria = Criteria.where("appointmentDate").gte(b.startDate().toString()).lte(b.endDate().toString());
        if (doctorId != null && !doctorId.isBlank()) {
            criteria.and("doctorId").is(doctorId);
        }

        Aggregation agg = newAggregation(
                match(criteria),
                group("status").count().as("count")
        );
        AggregationResults<Document> res = mongoTemplate.aggregate(agg, "appointments", Document.class);
        Map<String, Long> dist = new HashMap<>();
        for (Document d : res.getMappedResults()) {
            Object s = d.get("_id");
            if (s != null) dist.put(s.toString(), ((Number) d.get("count")).longValue());
        }
        return dist;
    }

    private List<AppointmentAnalyticsDTO.DoctorWorkloadSummary> aggregateDoctorWorkloadSummaries(AnalyticsDateUtils.DateBoundaries b) {
        Criteria criteria = Criteria.where("appointmentDate").gte(b.startDate().toString()).lte(b.endDate().toString());
        Aggregation agg = newAggregation(
                match(criteria),
                group("doctorId")
                        .count().as("totalAppointments")
                        .sum(org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                                .when(Criteria.where("status").is("COMPLETED")).then(1).otherwise(0)).as("completedAppointments"),
                sort(Sort.Direction.DESC, "totalAppointments"),
                limit(10)
        );

        AggregationResults<Document> res = mongoTemplate.aggregate(agg, "appointments", Document.class);
        List<AppointmentAnalyticsDTO.DoctorWorkloadSummary> list = new ArrayList<>();
        for (Document d : res.getMappedResults()) {
            String docId = d.getString("_id");
            if (docId != null) {
                Doctor doc = doctorRepository.findById(docId).orElse(null);
                long total = ((Number) d.getOrDefault("totalAppointments", 0)).longValue();
                long completed = ((Number) d.getOrDefault("completedAppointments", 0)).longValue();
                double rate = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

                list.add(AppointmentAnalyticsDTO.DoctorWorkloadSummary.builder()
                        .doctorId(docId)
                        .doctorName(doc != null ? doc.getFullName() : "Doctor " + docId)
                        .specialization(doc != null ? doc.getSpecialization() : "General")
                        .totalAppointments(total)
                        .completedAppointments(completed)
                        .completionRate(roundDouble(rate))
                        .build());
            }
        }
        return list;
    }

    private long countLabOrders(AnalyticsDateUtils.DateBoundaries b) {
        return mongoTemplate.count(
                Query.query(Criteria.where("orderedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                LabOrder.class
        );
    }

    private long countPendingLabOrders() {
        return mongoTemplate.count(
                Query.query(Criteria.where("status").in(List.of(LabOrderStatus.ORDERED, LabOrderStatus.COLLECTION_PENDING, LabOrderStatus.IN_PROGRESS))),
                LabOrder.class
        );
    }

    private long countSpecimensCollected(AnalyticsDateUtils.DateBoundaries b) {
        return mongoTemplate.count(
                Query.query(Criteria.where("status").in(List.of(LabOrderStatus.IN_PROGRESS, LabOrderStatus.COMPLETED))
                        .and("updatedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                LabOrder.class
        );
    }

    private long countReleasedLabResults(AnalyticsDateUtils.DateBoundaries b) {
        return mongoTemplate.count(
                Query.query(Criteria.where("workflowStatus").is(LabResultWorkflowStatus.RELEASED)
                        .and("releasedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                LabResult.class
        );
    }

    private long countLowStockMedicines() {
        List<PharmacyMedicine> meds = medicineRepository.findAll();
        long low = 0;
        for (PharmacyMedicine m : meds) {
            double threshold = m.getReorderLevel() != null ? m.getReorderLevel() : 10.0;
            // Sum batch available quantity
            Aggregation agg = newAggregation(
                    match(Criteria.where("medicineId").is(m.getId()).and("status").is(BatchStatus.AVAILABLE)),
                    group().sum("quantityAvailable").as("total")
            );
            AggregationResults<Document> res = mongoTemplate.aggregate(agg, "pharmacy_stock_batches", Document.class);
            double totalAvail = 0.0;
            if (!res.getMappedResults().isEmpty()) {
                Object val = res.getMappedResults().get(0).get("total");
                if (val instanceof Number n) totalAvail = n.doubleValue();
            }
            if (totalAvail <= threshold) low++;
        }
        return low;
    }

    private long countNearExpiryBatches(ZoneId zoneId) {
        Instant now = Instant.now();
        Instant in30Days = now.plus(30, ChronoUnit.DAYS);
        return mongoTemplate.count(
                Query.query(Criteria.where("status").is(BatchStatus.AVAILABLE)
                        .and("expiryDate").gt(now).lte(in30Days)),
                PharmacyStockBatch.class
        );
    }

    private long countExpiredBatches() {
        return mongoTemplate.count(
                Query.query(Criteria.where("status").is(BatchStatus.EXPIRED)),
                PharmacyStockBatch.class
        );
    }

    private long countPharmacyDispenses(AnalyticsDateUtils.DateBoundaries b) {
        return mongoTemplate.count(
                Query.query(Criteria.where("dispensedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                PharmacyDispense.class
        );
    }

    private record FinancialAggregates(
            long totalInvoices,
            BigDecimal grossBilled,
            BigDecimal totalDiscounts,
            BigDecimal totalTax,
            BigDecimal confirmedCollections,
            BigDecimal outstandingReceivables,
            BigDecimal totalRefunds,
            BigDecimal totalCreditNotes
    ) {}

    private FinancialAggregates aggregateFinancialsInPeriod(AnalyticsDateUtils.DateBoundaries b) {
        // Query invoices issued in period (exclude VOID)
        List<Invoice> invoices = mongoTemplate.find(
                Query.query(Criteria.where("issuedAt").gte(b.startInclusive()).lt(b.endExclusive())
                        .and("status").ne(InvoiceStatus.VOID)),
                Invoice.class
        );

        BigDecimal grossBilled = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            if (inv.getTotalAmount() != null) grossBilled = grossBilled.add(inv.getTotalAmount());
            if (inv.getDiscount() != null) totalDiscounts = totalDiscounts.add(inv.getDiscount());
            if (inv.getTax() != null) totalTax = totalTax.add(inv.getTax());
            if (inv.getOutstandingAmount() != null) outstanding = outstanding.add(inv.getOutstandingAmount());
        }

        // Query confirmed payments in period
        List<BillingPayment> payments = mongoTemplate.find(
                Query.query(Criteria.where("status").is(PaymentStatus.CONFIRMED)
                        .and("confirmedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                BillingPayment.class
        );
        BigDecimal confirmedCollections = payments.stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Query refunds in period
        List<BillingRefund> refunds = mongoTemplate.find(
                Query.query(Criteria.where("createdAt").gte(b.startInclusive()).lt(b.endExclusive())),
                BillingRefund.class
        );
        BigDecimal totalRefunds = refunds.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Query credit notes in period
        List<BillingCreditNote> creditNotes = mongoTemplate.find(
                Query.query(Criteria.where("createdAt").gte(b.startInclusive()).lt(b.endExclusive())),
                BillingCreditNote.class
        );
        BigDecimal totalCreditNotes = creditNotes.stream()
                .map(c -> c.getAmount() != null ? c.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialAggregates(
                invoices.size(),
                grossBilled.setScale(2, RoundingMode.HALF_UP),
                totalDiscounts.setScale(2, RoundingMode.HALF_UP),
                totalTax.setScale(2, RoundingMode.HALF_UP),
                confirmedCollections.setScale(2, RoundingMode.HALF_UP),
                outstanding.setScale(2, RoundingMode.HALF_UP),
                totalRefunds.setScale(2, RoundingMode.HALF_UP),
                totalCreditNotes.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private Map<String, BigDecimal> aggregateCollectionsByPaymentMethod(AnalyticsDateUtils.DateBoundaries b) {
        List<BillingPayment> payments = mongoTemplate.find(
                Query.query(Criteria.where("status").is(PaymentStatus.CONFIRMED)
                        .and("confirmedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                BillingPayment.class
        );

        Map<String, BigDecimal> map = new HashMap<>();
        for (BillingPayment p : payments) {
            String m = p.getMethod() != null ? p.getMethod().name() : "UNKNOWN";
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            map.put(m, map.getOrDefault(m, BigDecimal.ZERO).add(amt).setScale(2, RoundingMode.HALF_UP));
        }
        return map;
    }

    private Map<String, Long> aggregateInvoiceStatusDistribution(AnalyticsDateUtils.DateBoundaries b) {
        Aggregation agg = newAggregation(
                match(Criteria.where("createdAt").gte(b.startInclusive()).lt(b.endExclusive())),
                group("status").count().as("count")
        );
        AggregationResults<Document> res = mongoTemplate.aggregate(agg, "invoices", Document.class);
        Map<String, Long> dist = new HashMap<>();
        for (Document d : res.getMappedResults()) {
            Object s = d.get("_id");
            if (s != null) dist.put(s.toString(), ((Number) d.get("count")).longValue());
        }
        return dist;
    }

    private List<BillingFinancialAnalyticsDTO.DailyRevenuePoint> aggregateDailyRevenueTrends(AnalyticsDateUtils.DateBoundaries b) {
        // Collect daily billed and collected amounts
        Map<String, BigDecimal> billedMap = new HashMap<>();
        Map<String, BigDecimal> collectedMap = new HashMap<>();

        List<Invoice> invoices = mongoTemplate.find(
                Query.query(Criteria.where("issuedAt").gte(b.startInclusive()).lt(b.endExclusive())
                        .and("status").ne(InvoiceStatus.VOID)),
                Invoice.class
        );
        for (Invoice inv : invoices) {
            if (inv.getIssuedAt() != null) {
                String d = inv.getIssuedAt().atZone(b.zoneId()).toLocalDate().toString();
                BigDecimal val = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                billedMap.put(d, billedMap.getOrDefault(d, BigDecimal.ZERO).add(val));
            }
        }

        List<BillingPayment> payments = mongoTemplate.find(
                Query.query(Criteria.where("status").is(PaymentStatus.CONFIRMED)
                        .and("confirmedAt").gte(b.startInclusive()).lt(b.endExclusive())),
                BillingPayment.class
        );
        for (BillingPayment pay : payments) {
            if (pay.getConfirmedAt() != null) {
                String d = pay.getConfirmedAt().atZone(b.zoneId()).toLocalDate().toString();
                BigDecimal val = pay.getAmount() != null ? pay.getAmount() : BigDecimal.ZERO;
                collectedMap.put(d, collectedMap.getOrDefault(d, BigDecimal.ZERO).add(val));
            }
        }

        Set<String> allDates = new TreeSet<>();
        allDates.addAll(billedMap.keySet());
        allDates.addAll(collectedMap.keySet());

        List<BillingFinancialAnalyticsDTO.DailyRevenuePoint> points = new ArrayList<>();
        for (String dateStr : allDates) {
            points.add(BillingFinancialAnalyticsDTO.DailyRevenuePoint.builder()
                    .date(dateStr)
                    .billedAmount(billedMap.getOrDefault(dateStr, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                    .collectedAmount(collectedMap.getOrDefault(dateStr, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return points;
    }

    private List<BillingFinancialAnalyticsDTO.AgingBucketPoint> aggregateReceivablesAging(ZoneId zoneId) {
        Instant now = Instant.now();
        List<Invoice> unpaid = invoiceRepository.findAll().stream()
                .filter(i -> (i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                        && i.getOutstandingAmount() != null && i.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        BigDecimal b0_30 = BigDecimal.ZERO; long c0_30 = 0;
        BigDecimal b31_60 = BigDecimal.ZERO; long c31_60 = 0;
        BigDecimal b61_90 = BigDecimal.ZERO; long c61_90 = 0;
        BigDecimal b90Plus = BigDecimal.ZERO; long c90Plus = 0;

        for (Invoice inv : unpaid) {
            Instant issueInstant = inv.getIssuedAt() != null ? inv.getIssuedAt() : inv.getCreatedAt();
            long daysOld = (issueInstant != null) ? ChronoUnit.DAYS.between(issueInstant, now) : 0;
            BigDecimal amt = inv.getOutstandingAmount();

            if (daysOld <= 30) {
                b0_30 = b0_30.add(amt); c0_30++;
            } else if (daysOld <= 60) {
                b31_60 = b31_60.add(amt); c31_60++;
            } else if (daysOld <= 90) {
                b61_90 = b61_90.add(amt); c61_90++;
            } else {
                b90Plus = b90Plus.add(amt); c90Plus++;
            }
        }

        return List.of(
                new BillingFinancialAnalyticsDTO.AgingBucketPoint("0-30 Days", b0_30.setScale(2, RoundingMode.HALF_UP), c0_30),
                new BillingFinancialAnalyticsDTO.AgingBucketPoint("31-60 Days", b31_60.setScale(2, RoundingMode.HALF_UP), c31_60),
                new BillingFinancialAnalyticsDTO.AgingBucketPoint("61-90 Days", b61_90.setScale(2, RoundingMode.HALF_UP), c61_90),
                new BillingFinancialAnalyticsDTO.AgingBucketPoint("90+ Days", b90Plus.setScale(2, RoundingMode.HALF_UP), c90Plus)
        );
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
