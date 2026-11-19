package com.hospital.backend.controller;

import com.hospital.backend.dto.AppointmentRequest;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.User;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AuthorizationService authz;

    @PostMapping
    public ResponseEntity<Appointment> createAppointment(@Valid @RequestBody AppointmentRequest request) {
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.PATIENT) {
            authz.checkPatientAccess(request.getPatientId());
        }
        return ResponseEntity.ok(appointmentService.createAppointment(request));
    }

    @GetMapping
    public ResponseEntity<List<Appointment>> getAllAppointments() {
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.ok(appointmentService.getAllAppointments());
        } else if (user.getRole() == Role.DOCTOR) {
            return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorId(user.getId()));
        } else {
            return ResponseEntity.ok(appointmentService.getAppointmentsByPatientId(user.getId()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable String id) {
        Appointment appointment = appointmentService.getAppointmentById(id);
        authz.checkAppointmentAccess(appointment);
        return ResponseEntity.ok(appointment);
    }

    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.isDoctorOwner(#doctorId)")
    public ResponseEntity<List<Appointment>> getAppointmentsByDoctorId(@PathVariable String doctorId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorId(doctorId));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('ADMIN') or @authz.canAccessPatient(#patientId)")
    public ResponseEntity<List<Appointment>> getAppointmentsByPatientId(@PathVariable String patientId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatientId(patientId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Appointment> updateAppointmentStatus(
            @PathVariable String id,
            @RequestParam String status
    ) {
        Appointment appointment = appointmentService.getAppointmentById(id);
        User user = authz.getCurrentUser();
        if (user.getRole() == Role.PATIENT) {
            if (!"CANCELLED".equalsIgnoreCase(status)) {
                throw new AccessDeniedException("Patients can only change appointment status to CANCELLED");
            }
            authz.checkAppointmentCancellation(appointment);
        } else if (user.getRole() == Role.DOCTOR) {
            authz.checkAppointmentAccess(appointment);
        }
        return ResponseEntity.ok(appointmentService.updateAppointmentStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAppointment(@PathVariable String id) {
        Appointment appointment = appointmentService.getAppointmentById(id);
        authz.checkAppointmentCancellation(appointment);
        appointmentService.deleteAppointment(id);
        return ResponseEntity.ok("Appointment deleted successfully");
    }
}