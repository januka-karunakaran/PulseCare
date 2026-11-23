package com.hospital.backend.controller;

import com.hospital.backend.dto.pharmacy.*;
import com.hospital.backend.model.PharmacyMedicine;
import com.hospital.backend.model.PharmacyStockBatch;
import com.hospital.backend.model.PharmacyStockMovement;
import com.hospital.backend.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
@Slf4j
public class PharmacyController {

    private final PharmacyService pharmacyService;

    // =========================================================================
    // 1. Medicine Catalog Endpoints
    // =========================================================================

    @PostMapping("/medicines")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyMedicine> createMedicine(@Valid @RequestBody CreateMedicineRequest request) {
        PharmacyMedicine created = pharmacyService.createMedicine(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/medicines/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyMedicine> updateMedicine(
            @PathVariable String id,
            @Valid @RequestBody UpdateMedicineRequest request) {
        return ResponseEntity.ok(pharmacyService.updateMedicine(id, request));
    }

    @DeleteMapping("/medicines/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyMedicine> deactivateMedicine(@PathVariable String id) {
        return ResponseEntity.ok(pharmacyService.deactivateMedicine(id));
    }

    @GetMapping("/medicines/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST', 'PATIENT')")
    public ResponseEntity<MedicineResponseDTO> getMedicineById(@PathVariable String id) {
        return ResponseEntity.ok(pharmacyService.getMedicineById(id));
    }

    @GetMapping("/medicines")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST', 'PATIENT')")
    public ResponseEntity<Page<MedicineResponseDTO>> getAllMedicines(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "genericName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(pharmacyService.getAllMedicines(pageRequest, search));
    }

    @GetMapping("/medicines/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<List<MedicineResponseDTO>> getLowStockMedicines() {
        return ResponseEntity.ok(pharmacyService.getLowStockMedicines());
    }

    // =========================================================================
    // 2. Stock and Batch Endpoints
    // =========================================================================

    @PostMapping("/inventory/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyStockBatch> receiveStock(@Valid @RequestBody ReceiveStockRequest request) {
        PharmacyStockBatch batch = pharmacyService.receiveStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(batch);
    }

    @PostMapping("/inventory/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PharmacyStockBatch> adjustStock(@Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(pharmacyService.adjustStock(request));
    }

    @GetMapping("/medicines/{id}/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<List<BatchResponseDTO>> getBatchesByMedicine(@PathVariable String id) {
        return ResponseEntity.ok(pharmacyService.getBatchesByMedicine(id));
    }

    @GetMapping("/batches/near-expiry")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<List<BatchResponseDTO>> getNearExpiryBatches(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(pharmacyService.getNearExpiryBatches(days));
    }

    @GetMapping("/medicines/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<Page<PharmacyStockMovement>> getStockMovements(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ResponseEntity.ok(pharmacyService.getStockMovements(id, pageRequest));
    }

    // =========================================================================
    // 3. Prescription Queue Endpoints
    // =========================================================================

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<Page<PrescriptionQueueItemDTO>> getPrescriptionQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(pharmacyService.getPrescriptionQueue(pageRequest));
    }

    @GetMapping("/queue/{prescriptionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<PrescriptionQueueItemDTO> getPrescriptionQueueItem(
            @PathVariable String prescriptionId) {
        return ResponseEntity.ok(pharmacyService.getPrescriptionQueueItem(prescriptionId));
    }

    // =========================================================================
    // 4. Dispensing Endpoints
    // =========================================================================

    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<DispenseResponseDTO> dispenseMedication(
            @Valid @RequestBody DispenseMedicationRequest request) {
        DispenseResponseDTO response = pharmacyService.dispenseMedication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<DispenseResponseDTO> reverseDispense(
            @Valid @RequestBody ReverseDispenseRequest request) {
        return ResponseEntity.ok(pharmacyService.reverseDispense(request));
    }

    @GetMapping("/dispense/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST', 'PATIENT')")
    public ResponseEntity<DispenseResponseDTO> getDispenseById(@PathVariable String id) {
        return ResponseEntity.ok(pharmacyService.getDispenseById(id));
    }

    @GetMapping("/patients/{patientId}/dispenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST', 'PATIENT')")
    public ResponseEntity<Page<DispenseResponseDTO>> getPatientDispenses(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dispensedAt"));
        return ResponseEntity.ok(pharmacyService.getDispensesByPatient(patientId, pageRequest));
    }
}
