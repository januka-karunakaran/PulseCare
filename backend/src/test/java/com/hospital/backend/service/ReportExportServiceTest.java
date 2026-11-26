package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.*;
import com.hospital.backend.enums.ReportFormat;
import com.hospital.backend.enums.ReportType;
import com.hospital.backend.enums.ReportingDateRange;
import com.hospital.backend.service.impl.ReportExportServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportExportServiceTest {

    private final ReportExportServiceImpl exportService = new ReportExportServiceImpl();

    @Test
    @DisplayName("CSV Export: Prevents formula injection and handles quotes properly")
    void testCsvExport_FormulaInjectionNeutralized() {
        ReportExportRequest req = ReportExportRequest.builder()
                .reportType(ReportType.EXECUTIVE_OVERVIEW)
                .format(ReportFormat.CSV)
                .dateRange(ReportingDateRange.LAST_30_DAYS)
                .build();

        ExecutiveOverviewDTO dto = ExecutiveOverviewDTO.builder()
                .totalRegisteredPatients(150L)
                .newlyRegisteredPatients(25L)
                .registrationTimestampStatus("=cmd|' /C calc'!A0") // Malicious input
                .activePatientsPeriod(40L)
                .grossBilledAmount(new BigDecimal("1000.00"))
                .currency("LKR")
                .build();

        byte[] csvBytes = exportService.generateReport(req, dto);
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

        assertNotNull(csvContent);
        // Formula injection prefix should be neutralized with leading single quote
        assertTrue(csvContent.contains("'=cmd|' /C calc'!A0") || csvContent.contains("\"'=cmd|"));
    }

    @Test
    @DisplayName("PDF Export: Multi-page document generation succeeds without clipping or encoding error")
    void testPdfExport_MultiPageDocument_Success() throws Exception {
        ReportExportRequest req = ReportExportRequest.builder()
                .reportType(ReportType.FINANCIAL_RECONCILIATION)
                .format(ReportFormat.PDF)
                .dateRange(ReportingDateRange.THIS_MONTH)
                .build();

        List<FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem> anomalies = new ArrayList<>();
        // Generate 60 anomaly items to force multiple page breaks and headers
        for (int i = 1; i <= 60; i++) {
            anomalies.add(FinancialReconciliationDashboardDTO.ReconciliationAnomalyItem.builder()
                    .anomalyType("TEST_ANOMALY_" + i)
                    .severity("HIGH")
                    .referenceEntity("PAYMENT")
                    .referenceNumber("PAY-TEST-" + i)
                    .expectedAmount(new BigDecimal("100.00"))
                    .actualAmount(new BigDecimal("0.00"))
                    .description("Detailed discrepancy description for row " + i)
                    .build());
        }

        FinancialReconciliationDashboardDTO dto = FinancialReconciliationDashboardDTO.builder()
                .totalInconsistenciesDetected(60L)
                .examinedRecordsCount(500L)
                .returnedAnomaliesCount(60L)
                .totalInvoicesAudited(200L)
                .totalPaymentsAudited(250L)
                .totalRefundsAudited(50L)
                .truncated(false)
                .anomalies(anomalies)
                .build();

        byte[] pdfBytes = exportService.generateReport(req, dto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        // Verify PDFBox can parse it and multiple pages were created
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            assertTrue(doc.getNumberOfPages() >= 2, "Expected multiple pages for 60 anomalies, got: " + doc.getNumberOfPages());
        }
    }
}
