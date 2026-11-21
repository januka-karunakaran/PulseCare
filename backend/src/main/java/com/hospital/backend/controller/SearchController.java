package com.hospital.backend.controller;

import com.hospital.backend.dto.*;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.User;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for search and filtering operations
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    
    private final SearchService searchService;
    private final AuthorizationService authz;
    
    // ==================== Doctor Search Endpoints ====================
    
    /**
     * Search doctors with multiple criteria (name, specialization, rating, etc)
     */
    @PostMapping("/doctors")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<SearchDoctorResponse>> searchDoctors(
            @RequestBody SearchDoctorRequest request) {
        return ResponseEntity.ok(searchService.searchDoctors(request));
    }
    
    /**
     * Search doctors by specialization
     */
    @GetMapping("/doctors/specialization/{specialization}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<SearchDoctorResponse>> searchDoctorsBySpecialization(
            @PathVariable String specialization) {
        return ResponseEntity.ok(searchService.searchDoctorsBySpecialization(specialization));
    }
    
    /**
     * Search doctors by name
     */
    @GetMapping("/doctors/name/{name}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<SearchDoctorResponse>> searchDoctorsByName(
            @PathVariable String name) {
        return ResponseEntity.ok(searchService.searchDoctorsByName(name));
    }

    /**
     * Search patients by name, email or phone (role-restricted)
     */
    @GetMapping("/patients")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<ProfileResponse>> searchPatients(
            @RequestParam String searchTerm,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        return ResponseEntity.ok(searchService.searchPatients(searchTerm, limit, offset));
    }
    
    // ==================== Appointment Filter Endpoints ====================
    
    /**
     * Filter appointments with multiple criteria
     */
    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterAppointments(
            @RequestBody AppointmentFilterRequest request) {
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            request.setDoctorId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterAppointments(request));
    }
    
    /**
     * Filter appointments by status (PENDING, COMPLETED, CANCELLED)
     */
    @GetMapping("/appointments/status/{status}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterAppointmentsByStatus(
            @PathVariable String status) {
        User user = authz.getCurrentUser();
        AppointmentFilterRequest request = AppointmentFilterRequest.builder()
                .status(status)
                .limit(20)
                .offset(0)
                .build();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            request.setDoctorId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterAppointments(request));
    }
    
    /**
     * Filter appointments by date range
     * Query params: startDate (yyyy-MM-dd), endDate (yyyy-MM-dd)
     */
    @GetMapping("/appointments/date-range")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterAppointmentsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        User user = authz.getCurrentUser();
        AppointmentFilterRequest request = AppointmentFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .limit(20)
                .offset(0)
                .build();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            request.setDoctorId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterAppointments(request));
    }
    
    // ==================== Prescription Filter Endpoints ====================
    
    /**
     * Filter prescriptions with multiple criteria
     */
    @PostMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterPrescriptions(
            @RequestBody PrescriptionFilterRequest request) {
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            request.setDoctorId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterPrescriptions(request));
    }
    
    /**
     * Filter prescriptions by doctor
     */
    @GetMapping("/prescriptions/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterPrescriptionsByDoctor(
            @PathVariable String doctorId) {
        User user = authz.getCurrentUser();
        PrescriptionFilterRequest request = PrescriptionFilterRequest.builder()
                .doctorId(doctorId)
                .limit(20)
                .offset(0)
                .build();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterPrescriptions(request));
    }
    
    /**
     * Filter prescriptions by medicine name
     */
    @GetMapping("/prescriptions/medicine/{medicine}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<SearchResultsWrapper<?>> filterPrescriptionsByMedicine(
            @PathVariable String medicine) {
        User user = authz.getCurrentUser();
        PrescriptionFilterRequest request = PrescriptionFilterRequest.builder()
                .medicine(medicine)
                .limit(20)
                .offset(0)
                .build();
        if (user.getRole() == Role.PATIENT) {
            request.setPatientId(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            request.setDoctorId(user.getId());
        }
        return ResponseEntity.ok(searchService.filterPrescriptions(request));
    }
}
