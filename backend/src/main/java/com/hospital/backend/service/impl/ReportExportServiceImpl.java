package com.hospital.backend.service.impl;

import com.hospital.backend.dto.analytics.ReportExportRequest;
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

            // Write Body depending on reportData type
            if (reportData instanceof Map<?, ?> map) {
                writer.println("Metric,Value");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    writer.println(sanitizeCsv(String.valueOf(entry.getKey())) + "," + sanitizeCsv(String.valueOf(entry.getValue())));
                }
            } else if (reportData instanceof List<?> list) {
                writer.println("Item Index,Details");
                int idx = 1;
                for (Object item : list) {
                    writer.println(idx++ + "," + sanitizeCsv(String.valueOf(item)));
                }
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
                stream.showText("EXECUTIVE METRICS SUMMARY");
                stream.newLine();
                stream.setFont(PDType1Font.HELVETICA, 10);

                if (reportData instanceof Map<?, ?> map) {
                    int lines = 0;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (lines++ > 30) break;
                        String key = String.valueOf(entry.getKey());
                        String val = String.valueOf(entry.getValue());
                        stream.showText("- " + key + ": " + val);
                        stream.newLine();
                    }
                } else {
                    String str = String.valueOf(reportData);
                    if (str.length() > 80) str = str.substring(0, 80) + "...";
                    stream.showText(str);
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
