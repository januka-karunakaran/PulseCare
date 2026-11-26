package com.hospital.backend.controller;

import com.hospital.backend.dto.bed.BedSummaryDTO;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.model.BedOccupancyEvent;
import com.hospital.backend.service.AdmissionService;
import com.hospital.backend.service.BedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beds")
@RequiredArgsConstructor
public class BedController {

    private final BedService bedService;
    private final AdmissionService admissionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Bed> addBed(@RequestBody Bed bed) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bedService.addBed(bed));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<List<Bed>> getAllBeds() {
        return ResponseEntity.ok(bedService.getAllBeds());
    }

    @GetMapping("/availability")
    public ResponseEntity<List<BedSummaryDTO>> getAvailableBeds() {
        return ResponseEntity.ok(bedService.getAvailableBeds());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<Bed> getBedById(@PathVariable String id) {
        return ResponseEntity.ok(bedService.getBedById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<Bed> updateBed(@PathVariable String id, @RequestBody Bed bed) {
        return ResponseEntity.ok(bedService.updateBed(id, bed));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBed(@PathVariable String id) {
        bedService.deleteBed(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/book")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<BedBooking> bookBed(@RequestBody BedBooking booking) {
        return ResponseEntity.ok(bedService.bookBed(booking));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#patientId)")
    public ResponseEntity<List<BedBooking>> getPatientBookings(@PathVariable String patientId) {
        return ResponseEntity.ok(bedService.getPatientBookings(patientId));
    }

    @PostMapping("/discharge/{bookingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<BedBooking> dischargePatient(@PathVariable String bookingId) {
        return ResponseEntity.ok(bedService.dischargePatient(bookingId));
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<List<BedBooking>> getAllBookings() {
        return ResponseEntity.ok(bedService.getAllBookings());
    }

    @PostMapping("/{id}/mark-cleaning")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<Bed> markBedCleaning(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.markBedCleaning(id));
    }

    @PostMapping("/{id}/mark-ready")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER')")
    public ResponseEntity<Bed> markBedReady(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.markBedReady(id));
    }

    @PostMapping("/{id}/mark-maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Bed> markBedMaintenance(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.markBedMaintenance(id));
    }

    @GetMapping("/{id}/occupancy-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'BED_MANAGER', 'DOCTOR')")
    public ResponseEntity<List<BedOccupancyEvent>> getBedOccupancyHistory(@PathVariable String id) {
        return ResponseEntity.ok(admissionService.getBedOccupancyHistory(id));
    }
}
