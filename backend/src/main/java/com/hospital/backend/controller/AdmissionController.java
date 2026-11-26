package com.hospital.backend.controller;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.dto.bed.AdmissionResponseDTO;
import com.hospital.backend.dto.bed.DischargeRequestDTO;
import com.hospital.backend.dto.bed.TransferRequestDTO;
import com.hospital.backend.model.BedOccupancyEvent;
import com.hospital.backend.model.BedTransferRecord;
import com.hospital.backend.model.User;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.AdmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;
    private final AuthorizationService authz;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<AdmissionResponseDTO> admitPatient(@Valid @RequestBody AdmissionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admissionService.admitPatient(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<List<AdmissionResponseDTO>> getActiveAdmissions() {
        return ResponseEntity.ok(admissionService.getActiveAdmissions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER', 'PATIENT')")
    public ResponseEntity<AdmissionResponseDTO> getAdmissionById(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.getAdmissionById(id));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AdmissionResponseDTO>> getMyAdmissions() {
        User user = authz.getCurrentUser();
        return ResponseEntity.ok(admissionService.getPatientAdmissions(user.getId()));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#patientId)")
    public ResponseEntity<List<AdmissionResponseDTO>> getPatientAdmissions(@PathVariable String patientId) {
        return ResponseEntity.ok(admissionService.getPatientAdmissions(patientId));
    }

    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<List<AdmissionResponseDTO>> getDoctorAdmissions(@PathVariable String doctorId) {
        return ResponseEntity.ok(admissionService.getDoctorAdmissions(doctorId));
    }

    @PostMapping("/{id}/transfers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<BedTransferRecord> transferBed(
            @PathVariable String id,
            @Valid @RequestBody TransferRequestDTO request) {
        return ResponseEntity.ok(admissionService.transferBed(id, request));
    }

    @GetMapping("/{id}/transfers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER', 'PATIENT')")
    public ResponseEntity<List<BedTransferRecord>> getAdmissionTransfers(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.getAdmissionTransfers(id));
    }

    @PostMapping("/{id}/discharge-ready")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<AdmissionResponseDTO> prepareDischarge(
            @PathVariable String id,
            @RequestBody(required = false) DischargeRequestDTO request) {
        return ResponseEntity.ok(admissionService.prepareDischarge(id, request != null ? request : new DischargeRequestDTO()));
    }

    @PostMapping("/{id}/discharge")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<AdmissionResponseDTO> dischargePatient(
            @PathVariable String id,
            @RequestBody(required = false) DischargeRequestDTO request) {
        return ResponseEntity.ok(admissionService.dischargePatient(id, request != null ? request : new DischargeRequestDTO()));
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER', 'PATIENT')")
    public ResponseEntity<List<BedOccupancyEvent>> getAdmissionEvents(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.getAdmissionEvents(id));
    }
}
