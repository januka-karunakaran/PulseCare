package com.hospital.backend.controller;

import com.hospital.backend.dto.analytics.*;
import com.hospital.backend.enums.ReportFormat;
import com.hospital.backend.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final com.hospital.backend.service.BedOccupancyAnalyticsService bedOccupancyAnalyticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExecutiveOverviewDTO> getExecutiveOverview(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getExecutiveOverview(filter));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<AppointmentAnalyticsDTO> getAppointmentAnalytics(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getAppointmentAnalytics(filter));
    }

    @GetMapping("/doctors/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorWorkloadDTO> getMyDoctorWorkload(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getDoctorWorkload(null, filter));
    }

    @GetMapping("/doctors/{doctorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorWorkloadDTO> getDoctorWorkload(
            @PathVariable String doctorId,
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getDoctorWorkload(doctorId, filter));
    }

    @GetMapping("/patients/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatientOperationsDTO> getPatientOperations(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getPatientOperations(filter));
    }

    @GetMapping("/beds")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<BedUtilizationDTO> getBedUtilization() {
        return ResponseEntity.ok(analyticsService.getBedUtilization());
    }

    @GetMapping("/beds/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO> getBedOccupancyHistory(
            @RequestParam(value = "preset", defaultValue = "7D") String preset,
            @RequestParam(value = "start", required = false) java.time.Instant start,
            @RequestParam(value = "end", required = false) java.time.Instant end,
            @RequestParam(value = "wardId", required = false) String wardId,
            @RequestParam(value = "bedType", required = false) String bedType,
            @RequestParam(value = "timeZone", defaultValue = "Asia/Colombo") String timeZone) {
        if (start != null && end != null) {
            return ResponseEntity.ok(bedOccupancyAnalyticsService.getHistoricalOccupancy(start, end, wardId, bedType, timeZone));
        }
        return ResponseEntity.ok(bedOccupancyAnalyticsService.getHistoricalOccupancyForPreset(preset, wardId, bedType, timeZone));
    }

    @GetMapping("/beds/occupancy")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO> getBedOccupancy(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "wardId", required = false) String wardId,
            @RequestParam(value = "bedType", required = false) String bedType,
            @RequestParam(value = "timeZone", defaultValue = "Asia/Colombo") String timeZone) {
        java.time.LocalDate date = dateStr != null ? java.time.LocalDate.parse(dateStr) : java.time.LocalDate.now();
        return ResponseEntity.ok(bedOccupancyAnalyticsService.getDailyOccupancy(date, wardId, bedType, timeZone));
    }

    @GetMapping("/beds/length-of-stay")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<java.util.Map<String, Object>> getBedLengthOfStay(
            @RequestParam(value = "preset", defaultValue = "30D") String preset,
            @RequestParam(value = "wardId", required = false) String wardId,
            @RequestParam(value = "bedType", required = false) String bedType,
            @RequestParam(value = "timeZone", defaultValue = "Asia/Colombo") String timeZone) {
        com.hospital.backend.dto.bed.HistoricalOccupancyReportDTO report = bedOccupancyAnalyticsService.getHistoricalOccupancyForPreset(preset, wardId, bedType, timeZone);
        return ResponseEntity.ok(java.util.Map.of(
                "averageLengthOfStayHours", report.getAverageLengthOfStayHours(),
                "averageLengthOfStayDays", Math.round((report.getAverageLengthOfStayHours() / 24.0) * 100.0) / 100.0,
                "totalAdmissions", report.getAdmissionsCount(),
                "totalDischarges", report.getDischargesCount(),
                "dataCompletenessStatus", report.getDataCompletenessStatus()
        ));
    }

    @GetMapping("/laboratory")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECH')")
    public ResponseEntity<LaboratoryAnalyticsDTO> getLaboratoryAnalytics(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getLaboratoryAnalytics(filter));
    }

    @GetMapping("/pharmacy")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyAnalyticsDTO> getPharmacyAnalytics(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getPharmacyAnalytics(filter));
    }

    @GetMapping("/billing")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<BillingFinancialAnalyticsDTO> getBillingAnalytics(
            @ModelAttribute AnalyticsFilterRequest filter) {
        return ResponseEntity.ok(analyticsService.getBillingAnalytics(filter));
    }

    @GetMapping("/billing/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_STAFF')")
    public ResponseEntity<FinancialReconciliationDashboardDTO> getFinancialReconciliation() {
        return ResponseEntity.ok(analyticsService.getFinancialReconciliation());
    }

    @PostMapping("/reports/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH', 'PHARMACIST', 'BILLING_STAFF')")
    public ResponseEntity<byte[]> exportReport(@Valid @RequestBody ReportExportRequest request) {
        byte[] bytes = analyticsService.exportReport(request);

        String contentType = request.getFormat() == ReportFormat.CSV ? "text/csv" : "application/pdf";
        String filename = "pulsecare-" + request.getReportType().name().toLowerCase() + "-" + System.currentTimeMillis() +
                (request.getFormat() == ReportFormat.CSV ? ".csv" : ".pdf");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(bytes);
    }
}
