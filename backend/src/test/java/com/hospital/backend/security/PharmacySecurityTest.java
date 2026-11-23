package com.hospital.backend.security;

import com.hospital.backend.enums.Role;
import com.hospital.backend.model.Doctor;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PharmacySecurityTest {

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

    private User pharmacistUser;
    private User adminUser;
    private User doctorUser;
    private User patientUser;

    @BeforeEach
    void setUp() {
        pharmacistUser = User.builder().id("u-pharm").email("pharm@hospital.org").role(Role.PHARMACIST).build();
        adminUser = User.builder().id("u-admin").email("admin@hospital.org").role(Role.ADMIN).build();
        doctorUser = User.builder().id("u-doc").email("doc@hospital.org").role(Role.DOCTOR).build();
        patientUser = User.builder().id("u-pat").email("pat@hospital.org").role(Role.PATIENT).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("PHARMACIST can manage catalog, inventory, and dispensing")
    void testPharmacistPermissions() {
        authenticate(pharmacistUser);

        assertTrue(authorizationService.canManagePharmacyCatalog());
        assertTrue(authorizationService.canManagePharmacyInventory());
        assertTrue(authorizationService.canDispenseMedication());
        assertTrue(authorizationService.canViewPharmacyQueue());
    }

    @Test
    @DisplayName("ADMIN has full pharmacy administrative authority")
    void testAdminPermissions() {
        authenticate(adminUser);

        assertTrue(authorizationService.canManagePharmacyCatalog());
        assertTrue(authorizationService.canManagePharmacyInventory());
        assertTrue(authorizationService.canDispenseMedication());
        assertTrue(authorizationService.canViewPharmacyQueue());
    }

    @Test
    @DisplayName("DOCTOR cannot manage inventory or dispense medication")
    void testDoctorCannotDispense() {
        authenticate(doctorUser);

        assertFalse(authorizationService.canManagePharmacyCatalog());
        assertFalse(authorizationService.canManagePharmacyInventory());
        assertFalse(authorizationService.canDispenseMedication());
        assertFalse(authorizationService.canViewPharmacyQueue());
    }

    @Test
    @DisplayName("PATIENT cannot manage inventory or dispense medication")
    void testPatientCannotDispense() {
        authenticate(patientUser);

        assertFalse(authorizationService.canManagePharmacyCatalog());
        assertFalse(authorizationService.canManagePharmacyInventory());
        assertFalse(authorizationService.canDispenseMedication());
        assertFalse(authorizationService.canViewPharmacyQueue());
    }

    @Test
    @DisplayName("PATIENT can only access their own dispense records (IDOR protection)")
    void testPatientDispenseAccessControl() {
        authenticate(patientUser);
        Patient patientProfile = Patient.builder().id("pat-1").userId("u-pat").build();
        when(patientRepository.findById("pat-1")).thenReturn(Optional.of(patientProfile));

        // Own dispense -> permitted
        assertTrue(authorizationService.canAccessDispense("pat-1", "doc-1"));

        // Another patient's dispense -> denied
        assertFalse(authorizationService.canAccessDispense("other-patient", "doc-1"));
    }
}
