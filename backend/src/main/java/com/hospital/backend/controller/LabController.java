package com.hospital.backend.controller;

import com.hospital.backend.dto.common.ApiResponse;
import com.hospital.backend.dto.lab.*;
import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.model.LabOrder;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.LabSpecimen;
import com.hospital.backend.model.LabTestDefinition;
import com.hospital.backend.service.LabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
@Slf4j
public class LabController {

    private final LabService labService;

    // =========================================================================
    // Catalog Endpoints
    // =========================================================================

    @GetMapping("/tests")
    public ResponseEntity<List<LabTestDefinition>> getActiveTests() {
        return ResponseEntity.ok(labService.getActiveTests());
    }

    @GetMapping("/tests/search")
    public ResponseEntity<Page<LabTestDefinition>> searchTests(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(labService.searchTests(query, PageRequest.of(page, size, Sort.by("testName").ascending())));
    }

    @GetMapping("/tests/{id}")
    public ResponseEntity<LabTestDefinition> getTestById(@PathVariable String id) {
        return ResponseEntity.ok(labService.getTestById(id));
    }

    @PostMapping("/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabTestDefinition> createTestDefinition(@Valid @RequestBody CreateLabTestRequest request) {
        return new ResponseEntity<>(labService.createTestDefinition(request), HttpStatus.CREATED);
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabTestDefinition> updateTestDefinition(
            @PathVariable String id,
            @Valid @RequestBody UpdateLabTestRequest request
    ) {
        return ResponseEntity.ok(labService.updateTestDefinition(id, request));
    }

    @DeleteMapping("/tests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateTestDefinition(@PathVariable String id) {
        labService.deactivateTestDefinition(id);
        return ResponseEntity.ok(ApiResponse.success("Laboratory test deactivated successfully", null));
    }

    // =========================================================================
    // Order Endpoints
    // =========================================================================

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<LabOrder> createOrder(@Valid @RequestBody CreateLabOrderRequest request) {
        return new ResponseEntity<>(labService.createOrder(request), HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<LabOrderResponseDTO>> getOrders(
            @RequestParam(required = false) LabOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(labService.getOrders(status, PageRequest.of(page, size, Sort.by("orderedAt").descending())));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<LabOrderResponseDTO> getOrderDetails(@PathVariable String id) {
        return ResponseEntity.ok(labService.getOrderDetails(id));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<LabOrder> cancelOrder(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : "Clinician cancelled";
        return ResponseEntity.ok(labService.cancelOrder(id, reason));
    }

    @GetMapping("/patients/{patientId}/orders")
    public ResponseEntity<List<LabOrderResponseDTO>> getOrdersForPatient(@PathVariable String patientId) {
        return ResponseEntity.ok(labService.getOrdersForPatient(patientId));
    }

    // =========================================================================
    // Specimen Endpoints
    // =========================================================================

    @PostMapping("/orders/{orderId}/specimens")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<LabSpecimen> collectSpecimen(
            @PathVariable String orderId,
            @Valid @RequestBody CollectSpecimenRequest request
    ) {
        return new ResponseEntity<>(labService.collectSpecimen(orderId, request), HttpStatus.CREATED);
    }

    @GetMapping("/orders/{orderId}/specimens")
    public ResponseEntity<List<LabSpecimen>> getSpecimensForOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(labService.getSpecimensForOrder(orderId));
    }

    @PutMapping("/specimens/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<LabSpecimen> receiveSpecimen(@PathVariable String id) {
        return ResponseEntity.ok(labService.receiveSpecimen(id));
    }

    @PutMapping("/specimens/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<LabSpecimen> rejectSpecimen(
            @PathVariable String id,
            @Valid @RequestBody RejectSpecimenRequest request
    ) {
        return ResponseEntity.ok(labService.rejectSpecimen(id, request));
    }

    // =========================================================================
    // Result & Verification Endpoints
    // =========================================================================

    @PostMapping("/orders/{orderId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<LabResult> enterResult(
            @PathVariable String orderId,
            @Valid @RequestBody EnterLabResultRequest request
    ) {
        return new ResponseEntity<>(labService.enterResult(orderId, request), HttpStatus.CREATED);
    }

    @GetMapping("/orders/{orderId}/results")
    public ResponseEntity<List<LabResult>> getResultsForOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(labService.getResultsForOrder(orderId));
    }

    @PutMapping("/results/{id}/draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<LabResult> saveResultDraft(
            @PathVariable String id,
            @Valid @RequestBody EnterLabResultRequest request
    ) {
        return ResponseEntity.ok(labService.saveResultDraft(id, request));
    }

    @PostMapping("/results/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<LabResult> verifyResult(
            @PathVariable String id,
            @RequestBody(required = false) VerifyLabResultRequest request
    ) {
        return ResponseEntity.ok(labService.verifyResult(id, request));
    }

    @PostMapping("/results/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<LabResult> releaseResult(@PathVariable String id) {
        return ResponseEntity.ok(labService.releaseResult(id));
    }

    @PostMapping("/results/{id}/correct")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<LabResult> correctResult(
            @PathVariable String id,
            @Valid @RequestBody CorrectLabResultRequest request
    ) {
        return new ResponseEntity<>(labService.correctResult(id, request), HttpStatus.CREATED);
    }

    @GetMapping("/results/{id}")
    public ResponseEntity<LabResult> getResultById(@PathVariable String id) {
        return ResponseEntity.ok(labService.getResultById(id));
    }

    @GetMapping("/patients/{patientId}/reports")
    public ResponseEntity<List<LabResult>> getPatientReleasedReports(@PathVariable String patientId) {
        return ResponseEntity.ok(labService.getPatientReleasedReports(patientId));
    }

    @GetMapping("/results/{id}/pdf")
    public ResponseEntity<byte[]> downloadReportPdf(@PathVariable String id) {
        byte[] pdfBytes = labService.generateReportPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "lab-report-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
