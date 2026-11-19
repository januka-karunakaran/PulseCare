package com.hospital.backend.security;

import com.hospital.backend.enums.Role;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccessControlSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @InjectMocks
    private AuthorizationService authz;

    private User patientAUser;
    private User patientBUser;
    private User doctorUser;
    private User adminUser;

    private Appointment appointmentA;
    private Prescription prescriptionA;
    private Invoice invoiceA;

    @BeforeEach
    public void setup() {
        patientAUser = User.builder()
                .id("patient-A-id")
                .email("patientA@test.com")
                .fullName("Patient A")
                .role(Role.PATIENT)
                .build();

        patientBUser = User.builder()
                .id("patient-B-id")
                .email("patientB@test.com")
                .fullName("Patient B")
                .role(Role.PATIENT)
                .build();

        doctorUser = User.builder()
                .id("doctor-1-id")
                .email("doctor1@test.com")
                .fullName("Dr. Smith")
                .role(Role.DOCTOR)
                .build();

        adminUser = User.builder()
                .id("admin-id")
                .email("admin@test.com")
                .fullName("Hospital Admin")
                .role(Role.ADMIN)
                .build();

        appointmentA = Appointment.builder()
                .id("appt-1")
                .patientId("patient-A-id")
                .doctorId("doctor-1-id")
                .appointmentDate("2026-11-20")
                .appointmentTime("10:00 AM")
                .status("PENDING")
                .build();

        prescriptionA = Prescription.builder()
                .id("presc-1")
                .patientId("patient-A-id")
                .doctorId("doctor-1-id")
                .diagnosis("Hypertension")
                .build();

        invoiceA = Invoice.builder()
                .id("inv-1")
                .patientId("patient-A-id")
                .totalAmount(150.0)
                .status("UNPAID")
                .build();
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    public void patientA_AccessingOwnAppointment_Succeeds() {
        authenticateAs(patientAUser);

        assertDoesNotThrow(() -> authz.checkAppointmentAccess(appointmentA));
    }

    @Test
    public void patientB_AccessingPatientAAppointment_ThrowsAccessDeniedException() {
        authenticateAs(patientBUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authz.checkAppointmentAccess(appointmentA));
        assertTrue(ex.getMessage().contains("You are not authorized to access this appointment"));
    }

    @Test
    public void patientB_AttemptingToCancelPatientAAppointment_ThrowsAccessDeniedException() {
        authenticateAs(patientBUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authz.checkAppointmentCancellation(appointmentA));
        assertTrue(ex.getMessage().contains("You are not authorized to cancel this appointment"));
    }

    @Test
    public void patientA_CanCancelOwnAppointment_Succeeds() {
        authenticateAs(patientAUser);

        assertDoesNotThrow(() -> authz.checkAppointmentCancellation(appointmentA));
    }

    @Test
    public void patientB_AccessingPatientAPrescription_ThrowsAccessDeniedException() {
        authenticateAs(patientBUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authz.checkPrescriptionAccess(prescriptionA));
        assertTrue(ex.getMessage().contains("You are not authorized to view or download this prescription"));
    }

    @Test
    public void patientA_AccessingOwnPrescription_Succeeds() {
        authenticateAs(patientAUser);

        assertDoesNotThrow(() -> authz.checkPrescriptionAccess(prescriptionA));
    }

    @Test
    public void patientB_AccessingPatientAInvoice_ThrowsAccessDeniedException() {
        authenticateAs(patientBUser);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authz.checkInvoiceAccess(invoiceA));
        assertTrue(ex.getMessage().contains("You are not authorized to access this billing invoice"));
    }

    @Test
    public void patientA_AccessingOwnInvoice_Succeeds() {
        authenticateAs(patientAUser);

        assertDoesNotThrow(() -> authz.checkInvoiceAccess(invoiceA));
    }

    @Test
    public void patientB_AccessingPatientAProfile_ReturnsFalse() {
        authenticateAs(patientBUser);
        when(patientRepository.findById("patient-A-id")).thenReturn(Optional.of(
                Patient.builder().userId("patient-A-id").email("patientA@test.com").build()
        ));

        boolean canAccess = authz.canAccessPatient("patient-A-id");
        assertFalse(canAccess);
    }

    @Test
    public void doctor_WithValidAppointment_CanAccessPatient() {
        authenticateAs(doctorUser);
        when(doctorRepository.findByUserId("doctor-1-id")).thenReturn(Optional.of(
                Doctor.builder().id("doctor-profile-1").userId("doctor-1-id").build()
        ));
        when(appointmentRepository.findByDoctorId("doctor-profile-1")).thenReturn(List.of(
                Appointment.builder().id("appt-1").doctorId("doctor-profile-1").patientId("patient-A-id").build()
        ));

        boolean canAccess = authz.canAccessPatient("patient-A-id");
        assertTrue(canAccess);
    }

    @Test
    public void doctor_WithoutClinicalRelationship_CannotAccessPatient() {
        authenticateAs(doctorUser);
        when(doctorRepository.findByUserId("doctor-1-id")).thenReturn(Optional.of(
                Doctor.builder().id("doctor-profile-1").userId("doctor-1-id").build()
        ));
        when(appointmentRepository.findByDoctorId("doctor-profile-1")).thenReturn(Collections.emptyList());
        when(prescriptionRepository.findByDoctorId("doctor-profile-1")).thenReturn(Collections.emptyList());

        boolean canAccess = authz.canAccessPatient("unrelated-patient-id");
        assertFalse(canAccess);
    }

    @Test
    public void admin_CanAccessAnyAppointmentAndPatientRecord() {
        authenticateAs(adminUser);

        assertDoesNotThrow(() -> authz.checkAppointmentAccess(appointmentA));
        assertDoesNotThrow(() -> authz.checkPrescriptionAccess(prescriptionA));
        assertDoesNotThrow(() -> authz.checkInvoiceAccess(invoiceA));
        assertTrue(authz.canAccessPatient("patient-A-id"));
        assertTrue(authz.canAccessPatient("patient-B-id"));
    }
}
