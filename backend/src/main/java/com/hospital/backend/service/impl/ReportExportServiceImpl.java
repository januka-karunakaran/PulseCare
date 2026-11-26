package com.hospital.backend.service.impl;

import com.hospital.backend.dto.analytics.*;
import com.hospital.backend.enums.ReportFormat;
import com.hospital.backend.service.ReportExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportExportServiceImpl implements ReportExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Colombo"));

    @Override
    public byte[] generateReport(ReportExportRequest request, Object reportData) {
        if (request.getFormat() == ReportFormat.CSV) {
            return generateCsv(request, reportData);
        } else {
            return generatePdf(request, reportData);
        }
    }

    private byte[] generateCsv(ReportExportRequest request, Object reportData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            // Write Metadata Header
            writer.println("# PulseCare Hospital Management System - Operational Report");
            writer.println("# Report Type: " + sanitizeCsv(request.getReportType().name()));
            writer.println("# Date Range: " + sanitizeCsv(request.getDateRange().name()));
            writer.println("# Generated At: " + sanitizeCsv(DATE_FMT.format(Instant.now())));
            writer.println("# Disclaimed: Operational and administrative statistics only. Not a legally certified clinical or financial audit.");
            writer.println();

            // Explicit Structured Columns per DTO type
            if (reportData instanceof ExecutiveOverviewDTO dto) {
                writer.println("Metric Category,Metric Name,Value,Units");
                writer.println("Patients,Total Registered Patients," + dto.getTotalRegisteredPatients() + ",Count");
                writer.println("Patients,Newly Registered (Period)," + (dto.getNewlyRegisteredPatients() != null ? dto.getNewlyRegisteredPatients() : "NOT_AVAILABLE") + ",Count");
                writer.println("Patients,Timestamp Completeness," + sanitizeCsv(dto.getRegistrationTimestampStatus()) + ",Status");
                writer.println("Patients,Consultations In Period," + dto.getActivePatientsPeriod() + ",Count");
                writer.println("Doctors,Total Doctors," + dto.getTotalDoctors() + ",Count");
                writer.println("Doctors,Active Doctors," + dto.getActiveDoctors() + ",Count");
                writer.println("Appointments,Today Volume," + dto.getAppointmentsToday() + ",Count");
                writer.println("Appointments,Period Volume," + dto.getAppointmentsPeriod() + ",Count");
                writer.println("Appointments,Completed," + dto.getCompletedAppointmentsPeriod() + ",Count");
                writer.println("Appointments,Cancelled," + dto.getCancelledAppointmentsPeriod() + ",Count");
                writer.println("Appointments,Completion Rate," + dto.getAppointmentCompletionRate() + ",Percent");
                writer.println("Beds,Total Beds," + dto.getTotalBeds() + ",Count");
                writer.println("Beds,Occupied Beds," + dto.getOccupiedBeds() + ",Count");
                writer.println("Beds,Occupancy Rate," + dto.getBedOccupancyRate() + ",Percent");
                writer.println("Laboratory,Total Orders," + dto.getTotalLabOrdersPeriod() + ",Count");
                writer.println("Laboratory,Released Results," + dto.getReleasedLabResultsPeriod() + ",Count");
                writer.println("Pharmacy,Active Medicines," + dto.getActiveMedicineCatalogCount() + ",Count");
                writer.println("Pharmacy,Low Stock Items," + dto.getLowStockMedicineCount() + ",Count");
                writer.println("Finance,Gross Billed," + dto.getGrossBilledAmount() + "," + dto.getCurrency());
                writer.println("Finance,Confirmed Collections," + dto.getConfirmedCollections() + "," + dto.getCurrency());
                writer.println("Finance,Outstanding Receivables," + dto.getOutstandingReceivables() + "," + dto.getCurrency());
                writer.println("Finance,Refunds Issued," + dto.getTotalRefunds() + "," + dto.getCurrency());
            } else if (reportData instanceof AppointmentAnalyticsDTO dto) {
                writer.println("Section,Dimension,Metric,Value");
                writer.println("Summary,Total,Appointments," + dto.getTotalAppointments());
                writer.println("Summary,Completed,Appointments," + dto.getCompletedAppointments());
                writer.println("Summary,Cancelled,Appointments," + dto.getCancelledAppointments());
                writer.println("Summary,Completion Rate,Percent," + dto.getCompletionRate());
                writer.println();
                writer.println("Daily Trend,Date,Total,Completed,Cancelled");
                if (dto.getDailyTrends() != null) {
                    for (var pt : dto.getDailyTrends()) {
                        writer.println("Daily," + sanitizeCsv(pt.getDate()) + "," + pt.getTotal() + "," + pt.getCompleted() + "," + pt.getCancelled());
                    }
                }
            } else if (reportData instanceof BillingFinancialAnalyticsDTO dto) {
                writer.println("Metric Category,Metric Name,Amount,Currency");
                writer.println("Invoicing,Gross Billed," + dto.getGrossBilledAmount() + "," + dto.getCurrency());
                writer.println("Invoicing,Total Discounts," + dto.getTotalDiscounts() + "," + dto.getCurrency());
                writer.println("Invoicing,Total Tax," + dto.getTotalTax() + "," + dto.getCurrency());
                writer.println("Collections,Confirmed Collections," + dto.getConfirmedCollections() + "," + dto.getCurrency());
                writer.println("Receivables,Outstanding Receivables," + dto.getOutstandingReceivables() + "," + dto.getCurrency());
                writer.println("Adjustments,Total Refunds," + dto.getTotalRefunds() + "," + dto.getCurrency());
                writer.println();
                writer.println("Payment Method,Collected Amount,Currency");
                if (dto.getCollectionsByMethod() != null) {
                    for (var entry : dto.getCollectionsByMethod().entrySet()) {
                        writer.println(sanitizeCsv(entry.getKey()) + "," + entry.getValue() + "," + dto.getCurrency());
                    }
                }
            } else if (reportData instanceof FinancialReconciliationDashboardDTO dto) {
                writer.println("# Financial Reconciliation Summary");
                writer.println("Total Inconsistencies Detected," + dto.getTotalInconsistenciesDetected());
                writer.println("Examined Records Count," + dto.getExaminedRecordsCount());
                writer.println("Returned Anomalies Count," + dto.getReturnedAnomaliesCount());
                writer.println("Truncated Status," + dto.isTruncated());
                writer.println("Total Invoices Audited," + dto.getTotalInvoicesAudited());
                writer.println("Total Payments Audited," + dto.getTotalPaymentsAudited());
                writer.println("Total Refunds Audited," + dto.getTotalRefundsAudited());
                writer.println();
                writer.println("Anomaly Type,Severity,Reference Entity,Entity ID,Reference Number,Expected Amount,Actual Amount,Description");
                if (dto.getAnomalies() != null) {
                    for (var a : dto.getAnomalies()) {
                        writer.println(
                                sanitizeCsv(a.getAnomalyType()) + "," +
                                sanitizeCsv(a.getSeverity()) + "," +
                                sanitizeCsv(a.getReferenceEntity()) + "," +
                                sanitizeCsv(a.getEntityId()) + "," +
                                sanitizeCsv(a.getReferenceNumber()) + "," +
                                (a.getExpectedAmount() != null ? a.getExpectedAmount() : "0.00") + "," +
                                (a.getActualAmount() != null ? a.getActualAmount() : "0.00") + "," +
                                sanitizeCsv(a.getDescription())
                        );
                    }
                }
            } else if (reportData instanceof BedUtilizationDTO dto) {
                writer.println("Bed Metric,Value");
                writer.println("Total Beds," + dto.getTotalBeds());
                writer.println("Occupied Beds," + dto.getOccupiedBeds());
                writer.println("Available Beds," + dto.getAvailableBeds());
                writer.println("Maintenance Beds," + dto.getMaintenanceBeds());
                writer.println("Occupancy Rate (%)," + dto.getOccupancyRate());
                writer.println("Historical Log Status," + sanitizeCsv(dto.getHistoricalOccupancyStatus()));
            } else if (reportData instanceof LaboratoryAnalyticsDTO dto) {
                writer.println("Laboratory Metric,Value");
                writer.println("Total Orders," + dto.getTotalOrders());
                writer.println("Pending Collection," + dto.getPendingCollectionOrders());
                writer.println("Specimens Collected," + dto.getSpecimensCollected());
                writer.println("Specimens Rejected," + dto.getSpecimensRejected());
                writer.println("Results Verified," + dto.getResultsVerified());
                writer.println("Results Released," + dto.getResultsReleased());
                writer.println("Avg Turnaround Minutes," + dto.getAverageTurnaroundMinutes());
            } else if (reportData instanceof PharmacyAnalyticsDTO dto) {
                writer.println("Pharmacy Metric,Value");
                writer.println("Active Catalog Medicines," + dto.getActiveMedicineCatalogCount());
                writer.println("Available Stock Batches," + dto.getAvailableBatchesCount());
                writer.println("Distinct Medicines In Stock," + dto.getDistinctMedicinesInStock());
                writer.println("Low Stock Items," + dto.getLowStockItemsCount());
                writer.println("Near Expiry Batches," + dto.getNearExpiryBatchesCount());
                writer.println("Expired Batches," + dto.getExpiredBatchesCount());
                writer.println("Quarantined Batches," + dto.getQuarantinedBatchesCount());
                writer.println("Total Dispenses," + dto.getTotalDispenses());
                writer.println("Cost Basis Status," + sanitizeCsv(dto.getInventoryValuationStatus()));
                writer.println();
                writer.println("Stock By Unit,Measurement Unit,Available Quantity");
                if (dto.getStockByUnit() != null) {
                    for (var entry : dto.getStockByUnit().entrySet()) {
                        writer.println("Unit Stock," + sanitizeCsv(entry.getKey()) + "," + entry.getValue());
                    }
                }
            } else if (reportData instanceof PatientOperationsDTO dto) {
                writer.println("Patient Metric,Value");
                writer.println("Total Registered Patients," + dto.getTotalRegisteredPatients());
                writer.println("New Registrations (Period)," + (dto.getNewRegistrationsPeriod() != null ? dto.getNewRegistrationsPeriod() : "NOT_AVAILABLE"));
                writer.println("Timestamp Completeness Status," + sanitizeCsv(dto.getRegistrationTimestampStatus()));
                writer.println("Patients With Consultations," + dto.getPatientsWithConsultations());
            } else {
                writer.println("Property,Value");
                writer.println("Summary," + sanitizeCsv(String.valueOf(reportData)));
            }
        }
        return out.toByteArray();
    }

    private byte[] generatePdf(ReportExportRequest request, Object reportData) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String reportTitle = request.getReportType().name().replace("_", " ");
            String periodStr = request.getDateRange() != null ? request.getDateRange().name() : "CUSTOM";
            String generatedAtStr = DATE_FMT.format(Instant.now());

            try (PdfDocumentWriter writer = new PdfDocumentWriter(doc, reportTitle, periodStr, generatedAtStr)) {
                if (reportData instanceof ExecutiveOverviewDTO dto) {
                    writer.drawSectionTitle("EXECUTIVE SUMMARY METRICS");
                    String[] headers = {"Domain", "Key Metric Name", "Value", "Unit"};
                    float[] widths = {100f, 215f, 120f, 80f};
                    writer.drawTableHeader(headers, widths);

                    writer.drawTableRow(new String[]{"Patients", "Total Registered Patients", String.valueOf(dto.getTotalRegisteredPatients()), "Patients"}, widths);
                    writer.drawTableRow(new String[]{"Patients", "New Registrations (Period)", dto.getNewlyRegisteredPatients() != null ? String.valueOf(dto.getNewlyRegisteredPatients()) : "N/A (" + dto.getRegistrationTimestampStatus() + ")", "Patients"}, widths);
                    writer.drawTableRow(new String[]{"Patients", "Consultations (Period)", String.valueOf(dto.getActivePatientsPeriod()), "Patients"}, widths);
                    writer.drawTableRow(new String[]{"Doctors", "Active Doctors / Total", dto.getActiveDoctors() + " / " + dto.getTotalDoctors(), "Doctors"}, widths);
                    writer.drawTableRow(new String[]{"Appointments", "Period Volume (Completed/Cancelled)", dto.getCompletedAppointmentsPeriod() + " / " + dto.getCancelledAppointmentsPeriod() + " of " + dto.getAppointmentsPeriod(), "Count"}, widths);
                    writer.drawTableRow(new String[]{"Appointments", "Completion Rate", String.format("%.2f%%", dto.getAppointmentCompletionRate()), "Percent"}, widths);
                    writer.drawTableRow(new String[]{"Beds", "Occupancy Rate (Occupied/Total)", String.format("%.2f%% (%d/%d)", dto.getBedOccupancyRate(), dto.getOccupiedBeds(), dto.getTotalBeds()), "Percent"}, widths);
                    writer.drawTableRow(new String[]{"Laboratory", "Total Orders / Released", dto.getTotalLabOrdersPeriod() + " / " + dto.getReleasedLabResultsPeriod(), "Orders"}, widths);
                    writer.drawTableRow(new String[]{"Pharmacy", "Catalog Active / Low Stock", dto.getActiveMedicineCatalogCount() + " / " + dto.getLowStockMedicineCount(), "Medicines"}, widths);
                    writer.drawTableRow(new String[]{"Finance", "Gross Billed Amount", dto.getCurrency() + " " + dto.getGrossBilledAmount(), dto.getCurrency()}, widths);
                    writer.drawTableRow(new String[]{"Finance", "Confirmed Collections", dto.getCurrency() + " " + dto.getConfirmedCollections(), dto.getCurrency()}, widths);
                    writer.drawTableRow(new String[]{"Finance", "Outstanding Receivables", dto.getCurrency() + " " + dto.getOutstandingReceivables(), dto.getCurrency()}, widths);
                    writer.drawTableRow(new String[]{"Finance", "Refunds Issued", dto.getCurrency() + " " + dto.getTotalRefunds(), dto.getCurrency()}, widths);

                } else if (reportData instanceof AppointmentAnalyticsDTO dto) {
                    writer.drawSectionTitle("APPOINTMENT ACTIVITY SUMMARY");
                    String[] sumHeaders = {"Metric Name", "Value"};
                    float[] sumWidths = {260f, 255f};
                    writer.drawTableHeader(sumHeaders, sumWidths);
                    writer.drawTableRow(new String[]{"Total Appointments", String.valueOf(dto.getTotalAppointments())}, sumWidths);
                    writer.drawTableRow(new String[]{"Completed Appointments", String.valueOf(dto.getCompletedAppointments())}, sumWidths);
                    writer.drawTableRow(new String[]{"Cancelled Appointments", String.valueOf(dto.getCancelledAppointments())}, sumWidths);
                    writer.drawTableRow(new String[]{"Completion Rate", String.format("%.2f%%", dto.getCompletionRate())}, sumWidths);

                    if (dto.getDailyTrends() != null && !dto.getDailyTrends().isEmpty()) {
                        writer.drawSectionTitle("DAILY APPOINTMENT TRENDS");
                        String[] trendHeaders = {"Date", "Total Booked", "Completed", "Cancelled"};
                        float[] trendWidths = {130f, 130f, 130f, 125f};
                        writer.drawTableHeader(trendHeaders, trendWidths);
                        for (var pt : dto.getDailyTrends()) {
                            writer.drawTableRow(new String[]{
                                    pt.getDate(),
                                    String.valueOf(pt.getTotal()),
                                    String.valueOf(pt.getCompleted()),
                                    String.valueOf(pt.getCancelled())
                            }, trendWidths);
                        }
                    }

                } else if (reportData instanceof BillingFinancialAnalyticsDTO dto) {
                    writer.drawSectionTitle("FINANCIAL COLLECTIONS & RECEIVABLES");
                    String[] finHeaders = {"Financial Category", "Metric Name", "Amount", "Currency"};
                    float[] finWidths = {130f, 185f, 120f, 80f};
                    writer.drawTableHeader(finHeaders, finWidths);

                    writer.drawTableRow(new String[]{"Billing", "Gross Billed Amount", String.valueOf(dto.getGrossBilledAmount()), dto.getCurrency()}, finWidths);
                    writer.drawTableRow(new String[]{"Discounts", "Total Discounts Granted", String.valueOf(dto.getTotalDiscounts()), dto.getCurrency()}, finWidths);
                    writer.drawTableRow(new String[]{"Taxation", "Total Tax Charged", String.valueOf(dto.getTotalTax()), dto.getCurrency()}, finWidths);
                    writer.drawTableRow(new String[]{"Collections", "Confirmed Collections", String.valueOf(dto.getConfirmedCollections()), dto.getCurrency()}, finWidths);
                    writer.drawTableRow(new String[]{"Receivables", "Outstanding Receivables", String.valueOf(dto.getOutstandingReceivables()), dto.getCurrency()}, finWidths);
                    writer.drawTableRow(new String[]{"Adjustments", "Total Refunds Issued", String.valueOf(dto.getTotalRefunds()), dto.getCurrency()}, finWidths);

                    if (dto.getCollectionsByMethod() != null && !dto.getCollectionsByMethod().isEmpty()) {
                        writer.drawSectionTitle("COLLECTIONS BY PAYMENT CHANNEL");
                        String[] methHeaders = {"Payment Channel / Method", "Collected Amount", "Currency"};
                        float[] methWidths = {220f, 200f, 95f};
                        writer.drawTableHeader(methHeaders, methWidths);
                        for (var entry : dto.getCollectionsByMethod().entrySet()) {
                            writer.drawTableRow(new String[]{entry.getKey(), String.valueOf(entry.getValue()), dto.getCurrency()}, methWidths);
                        }
                    }

                } else if (reportData instanceof FinancialReconciliationDashboardDTO dto) {
                    writer.drawSectionTitle("FINANCIAL RECONCILIATION AUDIT SUMMARY");
                    String[] sumHeaders = {"Audit Metric", "Value"};
                    float[] sumWidths = {260f, 255f};
                    writer.drawTableHeader(sumHeaders, sumWidths);
                    writer.drawTableRow(new String[]{"Total Inconsistencies Detected", String.valueOf(dto.getTotalInconsistenciesDetected())}, sumWidths);
                    writer.drawTableRow(new String[]{"Examined Records Count", String.valueOf(dto.getExaminedRecordsCount())}, sumWidths);
                    writer.drawTableRow(new String[]{"Returned Anomalies Count", String.valueOf(dto.getReturnedAnomaliesCount())}, sumWidths);
                    writer.drawTableRow(new String[]{"Truncated Status", dto.isTruncated() ? "YES (Bounded to first 100 discrepancies)" : "NO (Complete)"}, sumWidths);
                    writer.drawTableRow(new String[]{"Invoices Audited", String.valueOf(dto.getTotalInvoicesAudited())}, sumWidths);
                    writer.drawTableRow(new String[]{"Payments Audited", String.valueOf(dto.getTotalPaymentsAudited())}, sumWidths);
                    writer.drawTableRow(new String[]{"Refunds Audited", String.valueOf(dto.getTotalRefundsAudited())}, sumWidths);

                    if (dto.getAnomalies() != null && !dto.getAnomalies().isEmpty()) {
                        writer.drawSectionTitle("DETECTED FINANCIAL DISCREPANCIES");
                        String[] anomHeaders = {"Type", "Severity", "Ref Number", "Expected", "Actual", "Description"};
                        float[] anomWidths = {115f, 50f, 85f, 65f, 65f, 135f};
                        writer.drawTableHeader(anomHeaders, anomWidths);
                        for (var a : dto.getAnomalies()) {
                            writer.drawTableRow(new String[]{
                                    a.getAnomalyType(),
                                    a.getSeverity(),
                                    a.getReferenceNumber() != null ? a.getReferenceNumber() : a.getEntityId(),
                                    a.getExpectedAmount() != null ? String.valueOf(a.getExpectedAmount()) : "0.00",
                                    a.getActualAmount() != null ? String.valueOf(a.getActualAmount()) : "0.00",
                                    a.getDescription()
                            }, anomWidths);
                        }
                    }

                } else if (reportData instanceof PharmacyAnalyticsDTO dto) {
                    writer.drawSectionTitle("PHARMACY INVENTORY & DISPENSING");
                    String[] phHeaders = {"Inventory Indicator", "Count / Value"};
                    float[] phWidths = {260f, 255f};
                    writer.drawTableHeader(phHeaders, phWidths);
                    writer.drawTableRow(new String[]{"Active Catalog Medicines", String.valueOf(dto.getActiveMedicineCatalogCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Available Stock Batches", String.valueOf(dto.getAvailableBatchesCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Distinct Medicines in Stock", String.valueOf(dto.getDistinctMedicinesInStock())}, phWidths);
                    writer.drawTableRow(new String[]{"Low Stock Medicines", String.valueOf(dto.getLowStockItemsCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Near Expiry Batches", String.valueOf(dto.getNearExpiryBatchesCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Expired Batches", String.valueOf(dto.getExpiredBatchesCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Quarantined Batches", String.valueOf(dto.getQuarantinedBatchesCount())}, phWidths);
                    writer.drawTableRow(new String[]{"Total Dispenses (Period)", String.valueOf(dto.getTotalDispenses())}, phWidths);

                    if (dto.getStockByUnit() != null && !dto.getStockByUnit().isEmpty()) {
                        writer.drawSectionTitle("STOCK BREAKDOWN BY MEASUREMENT UNIT");
                        String[] unitHeaders = {"Measurement Unit", "Available Quantity"};
                        float[] unitWidths = {260f, 255f};
                        writer.drawTableHeader(unitHeaders, unitWidths);
                        for (var entry : dto.getStockByUnit().entrySet()) {
                            writer.drawTableRow(new String[]{entry.getKey(), String.format("%.2f", entry.getValue())}, unitWidths);
                        }
                    }

                } else if (reportData instanceof BedUtilizationDTO dto) {
                    writer.drawSectionTitle("HOSPITAL BED OCCUPANCY");
                    String[] bedHeaders = {"Bed Metric", "Value"};
                    float[] bedWidths = {260f, 255f};
                    writer.drawTableHeader(bedHeaders, bedWidths);
                    writer.drawTableRow(new String[]{"Total Hospital Beds", String.valueOf(dto.getTotalBeds())}, bedWidths);
                    writer.drawTableRow(new String[]{"Occupied Beds", String.valueOf(dto.getOccupiedBeds())}, bedWidths);
                    writer.drawTableRow(new String[]{"Available Beds", String.valueOf(dto.getAvailableBeds())}, bedWidths);
                    writer.drawTableRow(new String[]{"Maintenance Beds", String.valueOf(dto.getMaintenanceBeds())}, bedWidths);
                    writer.drawTableRow(new String[]{"Bed Occupancy Rate", String.format("%.2f%%", dto.getOccupancyRate())}, bedWidths);

                } else if (reportData instanceof LaboratoryAnalyticsDTO dto) {
                    writer.drawSectionTitle("LABORATORY OPERATIONS & THROUGHPUT");
                    String[] labHeaders = {"Laboratory Metric", "Value"};
                    float[] labWidths = {260f, 255f};
                    writer.drawTableHeader(labHeaders, labWidths);
                    writer.drawTableRow(new String[]{"Total Orders", String.valueOf(dto.getTotalOrders())}, labWidths);
                    writer.drawTableRow(new String[]{"Pending Collection Orders", String.valueOf(dto.getPendingCollectionOrders())}, labWidths);
                    writer.drawTableRow(new String[]{"Specimens Collected", String.valueOf(dto.getSpecimensCollected())}, labWidths);
                    writer.drawTableRow(new String[]{"Specimens Rejected", String.valueOf(dto.getSpecimensRejected())}, labWidths);
                    writer.drawTableRow(new String[]{"Results Verified", String.valueOf(dto.getResultsVerified())}, labWidths);
                    writer.drawTableRow(new String[]{"Results Released", String.valueOf(dto.getResultsReleased())}, labWidths);
                    writer.drawTableRow(new String[]{"Average Turnaround Time", String.format("%.1f minutes", dto.getAverageTurnaroundMinutes())}, labWidths);

                } else if (reportData instanceof PatientOperationsDTO dto) {
                    writer.drawSectionTitle("PATIENT POPULATION OPERATIONS");
                    String[] patHeaders = {"Demographic Metric", "Value"};
                    float[] patWidths = {260f, 255f};
                    writer.drawTableHeader(patHeaders, patWidths);
                    writer.drawTableRow(new String[]{"Total Registered Patients", String.valueOf(dto.getTotalRegisteredPatients())}, patWidths);
                    writer.drawTableRow(new String[]{"New Registrations (Period)", dto.getNewRegistrationsPeriod() != null ? String.valueOf(dto.getNewRegistrationsPeriod()) : "N/A (" + dto.getRegistrationTimestampStatus() + ")"}, patWidths);
                    writer.drawTableRow(new String[]{"Patients With Consultations", String.valueOf(dto.getPatientsWithConsultations())}, patWidths);

                } else {
                    writer.drawSectionTitle("OPERATIONAL SUMMARY");
                    String[] genHeaders = {"Property", "Value"};
                    float[] genWidths = {200f, 315f};
                    writer.drawTableHeader(genHeaders, genWidths);
                    writer.drawTableRow(new String[]{"Summary Data", String.valueOf(reportData)}, genWidths);
                }
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage());
        }
    }

    /**
     * Prevent CSV Formula Injection (=, +, -, @, \t, \r)
     * and handle quotes, commas, newlines properly.
     */
    private String sanitizeCsv(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\r\n", " ").replace("\r", " ").replace("\n", " ").replace("\t", " ");
        String trimmed = cleaned.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            cleaned = "'" + cleaned;
        }
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("'")) {
            cleaned = "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private static String sanitizePdfText(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\x20-\\x7E]", " ").trim();
    }

    private static String truncateText(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static class PdfDocumentWriter implements AutoCloseable {
        private final PDDocument doc;
        private PDPage currentPage;
        private PDPageContentStream stream;
        private float y;
        private final float margin = 40f;
        private final float pageWidth;
        private final float pageHeight;
        private final String reportType;
        private final String dateRange;
        private final String generatedTime;
        private int pageNumber = 0;

        public PdfDocumentWriter(PDDocument doc, String reportType, String dateRange, String generatedTime) throws Exception {
            this.doc = doc;
            this.reportType = reportType;
            this.dateRange = dateRange;
            this.generatedTime = generatedTime;
            this.pageWidth = PDRectangle.A4.getWidth();
            this.pageHeight = PDRectangle.A4.getHeight();
            newPage();
        }

        private void newPage() throws Exception {
            if (stream != null) {
                drawFooter();
                stream.close();
            }
            pageNumber++;
            currentPage = new PDPage(PDRectangle.A4);
            doc.addPage(currentPage);
            stream = new PDPageContentStream(doc, currentPage);
            y = pageHeight - margin;

            // Draw header
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA_BOLD, 13);
            stream.newLineAtOffset(margin, y);
            stream.showText("PulseCare 2.0 - " + sanitizePdfText(reportType));
            stream.endText();
            y -= 15;

            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 8.5f);
            stream.newLineAtOffset(margin, y);
            stream.showText("Period: " + sanitizePdfText(dateRange) + " | Generated: " + sanitizePdfText(generatedTime) + " (Asia/Colombo)");
            stream.endText();
            y -= 13;

            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
            stream.newLineAtOffset(margin, y);
            stream.showText("Operational & Administrative Analytics (Non-PHI Aggregated Data)");
            stream.endText();
            y -= 16;

            // Header line
            stream.setLineWidth(0.6f);
            stream.setStrokingColor(180, 190, 205);
            stream.moveTo(margin, y);
            stream.lineTo(pageWidth - margin, y);
            stream.stroke();
            stream.setStrokingColor(0, 0, 0);
            y -= 15;
        }

        private void drawFooter() throws Exception {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 7.5f);
            stream.newLineAtOffset(margin, margin - 15);
            stream.showText("PulseCare Enterprise Analytics | Page " + pageNumber + " | Confidential - Internal Operations Only");
            stream.endText();
        }

        public void checkPageBreak(float requiredSpace) throws Exception {
            if (y - requiredSpace < margin + 25) {
                newPage();
            }
        }

        public void drawTableHeader(String[] headers, float[] colWidths) throws Exception {
            checkPageBreak(25);
            // Draw background rectangle
            stream.setNonStrokingColor(235, 240, 245);
            stream.addRect(margin, y - 4, pageWidth - 2 * margin, 15);
            stream.fill();
            stream.setNonStrokingColor(0, 0, 0);

            float curX = margin + 4;
            for (int i = 0; i < headers.length; i++) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 8.5f);
                stream.newLineAtOffset(curX, y);
                stream.showText(sanitizePdfText(truncateText(headers[i], (int)(colWidths[i] / 5.0))));
                stream.endText();
                curX += colWidths[i];
            }
            y -= 15;
        }

        public void drawTableRow(String[] cells, float[] colWidths) throws Exception {
            checkPageBreak(15);
            float curX = margin + 4;
            for (int i = 0; i < cells.length; i++) {
                String text = cells[i] != null ? cells[i] : "-";
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 8.0f);
                stream.newLineAtOffset(curX, y);
                stream.showText(sanitizePdfText(truncateText(text, (int)(colWidths[i] / 4.8))));
                stream.endText();
                curX += colWidths[i];
            }

            // separator line
            stream.setLineWidth(0.3f);
            stream.setStrokingColor(225, 230, 235);
            stream.moveTo(margin, y - 3);
            stream.lineTo(pageWidth - margin, y - 3);
            stream.stroke();
            stream.setStrokingColor(0, 0, 0);
            y -= 13;
        }

        public void drawSectionTitle(String title) throws Exception {
            checkPageBreak(30);
            y -= 5;
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
            stream.newLineAtOffset(margin, y);
            stream.showText(sanitizePdfText(title));
            stream.endText();
            y -= 12;
        }

        @Override
        public void close() throws Exception {
            if (stream != null) {
                drawFooter();
                stream.close();
            }
        }
    }
}
