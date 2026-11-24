package com.hospital.backend.security;

import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingAuthorizationSecurityTest {

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
    private AuthorizationService authorizationService;

    private User admin;
    private User billingStaff;
    private User doctor;
    private User patientA;
    private User patientB;

    private Patient patRecordA;
    private Patient patRecordB;
    private Invoice invoiceA;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        admin = User.builder().id("admin-id").email("admin@test.com").role(Role.ADMIN).fullName("Admin User").build();
        billingStaff = User.builder().id("bill-id").email("billing@test.com").role(Role.BILLING_STAFF).fullName("Billing Staff").build();
        doctor = User.builder().id("doc-id").email("doctor@test.com").role(Role.DOCTOR).fullName("Dr. Smith").build();
        patientA = User.builder().id("pat-user-A").email("patientA@test.com").role(Role.PATIENT).fullName("Patient A").build();
        patientB = User.builder().id("pat-user-B").email("patientB@test.com").role(Role.PATIENT).fullName("Patient B").build();

        patRecordA = Patient.builder().id("patient-rec-A").userId("pat-user-A").fullName("Patient A").build();
        patRecordB = Patient.builder().id("patient-rec-B").userId("pat-user-B").fullName("Patient B").build();

        invoiceA = Invoice.builder()
                .id("inv-A")
                .invoiceNumber("INV-2026-000001")
                .patientId("patient-rec-A")
                .status(InvoiceStatus.ISSUED)
                .totalAmount(new BigDecimal("150.00"))
                .outstandingAmount(new BigDecimal("150.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        lenient().when(patientRepository.findById("patient-rec-A")).thenReturn(Optional.of(patRecordA));
        lenient().when(patientRepository.findByUserId("pat-user-A")).thenReturn(Optional.of(patRecordA));
        lenient().when(patientRepository.findById("patient-rec-B")).thenReturn(Optional.of(patRecordB));
        lenient().when(patientRepository.findByUserId("pat-user-B")).thenReturn(Optional.of(patRecordB));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Admin and Billing Staff have permission to manage billing")
    void testCanManageBilling_AdminAndBillingStaff_Allowed() {
        authenticateAs(billingStaff);
        assertTrue(authorizationService.canManageBilling());

        authenticateAs(admin);
        assertTrue(authorizationService.canManageBilling());
    }

    @Test
    @DisplayName("Doctor and Patient cannot manage billing")
    void testCanManageBilling_DoctorAndPatient_Denied() {
        authenticateAs(doctor);
        assertFalse(authorizationService.canManageBilling());

        authenticateAs(patientA);
        assertFalse(authorizationService.canManageBilling());
    }

    @Test
    @DisplayName("Billing Staff and Admin can record payments")
    void testCanRecordPayments_Allowed() {
        authenticateAs(billingStaff);
        assertTrue(authorizationService.canRecordPayments());

        authenticateAs(admin);
        assertTrue(authorizationService.canRecordPayments());
    }

    @Test
    @DisplayName("Doctor cannot record payments")
    void testCanRecordPayments_DoctorDenied() {
        authenticateAs(doctor);
        assertFalse(authorizationService.canRecordPayments());
    }

    @Test
    @DisplayName("Patient can access their own finalized invoice")
    void testCheckInvoiceAccess_OwnInvoice_Allowed() {
        authenticateAs(patientA);
        assertDoesNotThrow(() -> authorizationService.checkInvoiceAccess(invoiceA));
    }

    @Test
    @DisplayName("Patient cannot access another patient's invoice (IDOR prevented)")
    void testCheckInvoiceAccess_OtherPatientInvoice_ThrowsAccessDenied() {
        authenticateAs(patientB);
        assertThrows(AccessDeniedException.class, () -> authorizationService.checkInvoiceAccess(invoiceA));
    }

    @Test
    @DisplayName("Billing Staff and Admin have unrestricted access to inspect patient invoices")
    void testCheckInvoiceAccess_BillingStaffAndAdmin_Allowed() {
        authenticateAs(billingStaff);
        assertDoesNotThrow(() -> authorizationService.checkInvoiceAccess(invoiceA));

        authenticateAs(admin);
        assertDoesNotThrow(() -> authorizationService.checkInvoiceAccess(invoiceA));
    }
}
