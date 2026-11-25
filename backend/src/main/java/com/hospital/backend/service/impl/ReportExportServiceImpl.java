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
                writer.println("Anomaly Type,Severity,Reference Entity,Entity ID,Reference Number,Expected Amount,Actual Amount,Description");
                if (dto.getAnomalies() != null) {
                    for (var a : dto.getAnomalies()) {
                        writer.println(
                                sanitizeCsv(a.getAnomalyType()) + "," +
                                sanitizeCsv(a.getSeverity()) + "," +
                                sanitizeCsv(a.getReferenceEntity()) + "," +
                                sanitizeCsv(a.getEntityId()) + "," +
                                sanitizeCsv(a.getReferenceNumber()) + "," +
                                a.getExpectedAmount() + "," +
                                a.getActualAmount() + "," +
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
                writer.println("Active Medicines," + dto.getActiveMedicineCatalogCount());
                writer.println("Available Medicine Stock Units," + dto.getAvailableMedicineStock());
                writer.println("Low Stock Items," + dto.getLowStockItemsCount());
                writer.println("Near Expiry Batches," + dto.getNearExpiryBatchesCount());
                writer.println("Expired Batches," + dto.getExpiredBatchesCount());
                writer.println("Quarantined Batches," + dto.getQuarantinedBatchesCount());
                writer.println("Total Dispenses," + dto.getTotalDispenses());
                writer.println("Cost Basis Status," + sanitizeCsv(dto.getInventoryValuationStatus()));
            } else {
                writer.println("Property,Value");
                writer.println("Summary," + sanitizeCsv(String.valueOf(reportData)));
            }
        }
        return out.toByteArray();
    }

    private byte[] generatePdf(ReportExportRequest request, Object reportData) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setLeading(14.0f);

                // Header
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                stream.newLineAtOffset(50, 740);
                stream.showText("PulseCare Health System");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.showText("OPERATIONAL ANALYTICS REPORT");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText("Report Type: " + request.getReportType().name() + " | Format: PDF");
                stream.newLine();
                stream.showText("Date Range: " + request.getDateRange().name() + " | Generated At: " + DATE_FMT.format(Instant.now()));
                stream.newLine();
                stream.showText("Scope: Administrative & Operational Metrics (Non-PHI Aggregates)");
                stream.newLine();
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                stream.showText("STRUCTURED REPORT CONTENT");
                stream.newLine();
                stream.setFont(PDType1Font.HELVETICA, 10);

                if (reportData instanceof ExecutiveOverviewDTO dto) {
                    stream.showText(String.format("- Total Patients: %d | New Registered: %s | Consultations: %d",
                            dto.getTotalRegisteredPatients(),
                            dto.getNewlyRegisteredPatients() != null ? dto.getNewlyRegisteredPatients() : "N/A",
                            dto.getActivePatientsPeriod()));
                    stream.newLine();
                    stream.showText(String.format("- Appointments Period: %d | Completed: %d | Rate: %.2f%%",
                            dto.getAppointmentsPeriod(), dto.getCompletedAppointmentsPeriod(), dto.getAppointmentCompletionRate()));
                    stream.newLine();
                    stream.showText(String.format("- Beds Total: %d | Occupied: %d | Available: %d | Occupancy Rate: %.2f%%",
                            dto.getTotalBeds(), dto.getOccupiedBeds(), dto.getAvailableBeds(), dto.getBedOccupancyRate()));
                    stream.newLine();
                    stream.showText(String.format("- Lab Orders: %d | Released: %d | Pharmacy Active Meds: %d",
                            dto.getTotalLabOrdersPeriod(), dto.getReleasedLabResultsPeriod(), dto.getActiveMedicineCatalogCount()));
                    stream.newLine();
                    stream.showText(String.format("- Billed: %s %s | Collections: %s %s | Receivables: %s %s",
                            dto.getCurrency(), dto.getGrossBilledAmount(),
                            dto.getCurrency(), dto.getConfirmedCollections(),
                            dto.getCurrency(), dto.getOutstandingReceivables()));
                    stream.newLine();
                } else if (reportData instanceof AppointmentAnalyticsDTO dto) {
                    stream.showText(String.format("- Total Appointments: %d | Completed: %d | Cancelled: %d",
                            dto.getTotalAppointments(), dto.getCompletedAppointments(), dto.getCancelledAppointments()));
                    stream.newLine();
                    stream.showText(String.format("- Completion Rate: %.2f%%", dto.getCompletionRate()));
                    stream.newLine();
                } else if (reportData instanceof BillingFinancialAnalyticsDTO dto) {
                    stream.showText(String.format("- Gross Billed: %s %s | Collections: %s %s",
                            dto.getCurrency(), dto.getGrossBilledAmount(), dto.getCurrency(), dto.getConfirmedCollections()));
                    stream.newLine();
                    stream.showText(String.format("- Outstanding Balance: %s %s | Refunds: %s %s",
                            dto.getCurrency(), dto.getOutstandingReceivables(), dto.getCurrency(), dto.getTotalRefunds()));
                    stream.newLine();
                } else if (reportData instanceof FinancialReconciliationDashboardDTO dto) {
                    stream.showText(String.format("- Total Discrepancies Detected: %d | Invoices Audited: %d | Payments: %d",
                            dto.getTotalInconsistenciesDetected(), dto.getTotalInvoicesAudited(), dto.getTotalPaymentsAudited()));
                    stream.newLine();
                } else {
                    stream.showText("- Operational Metrics Summary Generated.");
                    stream.newLine();
                }

                stream.newLine();
                stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                stream.showText("Notice: PulseCare operational analytics do not constitute clinical certification or legally binding financial advice.");
                stream.endText();
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
     */
    private String sanitizeCsv(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            trimmed = "'" + trimmed;
        }
        if (trimmed.contains(",") || trimmed.contains("\"") || trimmed.contains("\n")) {
            trimmed = "\"" + trimmed.replace("\"", "\"\"") + "\"";
        }
        return trimmed;
    }
}
