package com.hospital.backend.security;

import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.enums.LabResultWorkflowStatus;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabAuthorizationSecurityTest {

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

    private User patientA;
    private User patientB;
    private User doctorA;
    private User doctorB;
    private User labTech;
    private User admin;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        patientA = User.builder().id("pat-user-A").email("patientA@test.com").role(Role.PATIENT).fullName("Patient A").build();
        patientB = User.builder().id("pat-user-B").email("patientB@test.com").role(Role.PATIENT).fullName("Patient B").build();
        doctorA = User.builder().id("doc-user-A").email("doctorA@hospital.org").role(Role.DOCTOR).fullName("Dr. Doctor A").build();
        doctorB = User.builder().id("doc-user-B").email("doctorB@hospital.org").role(Role.DOCTOR).fullName("Dr. Doctor B").build();
        labTech = User.builder().id("tech-user-1").email("labtech@hospital.org").role(Role.LAB_TECH).fullName("Lab Tech 1").build();
        admin = User.builder().id("admin-user-1").email("admin@hospital.org").role(Role.ADMIN).fullName("Admin").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "credentials",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("E1: Patient A can access own released lab report")
    void patientA_AccessOwnReleasedReport_Succeeds() {
        authenticateAs(patientA);

        LabResult released = LabResult.builder()
                .id("res-1")
                .patientId("pat-user-A")
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .build();

        assertTrue(authorizationService.canAccessLabResult(released));
        assertDoesNotThrow(() -> authorizationService.checkLabResultAccess(released));
    }

    @Test
    @DisplayName("E2: Patient B cannot access Patient A's lab report (Throws 403 Forbidden)")
    void patientB_AccessPatientAReport_ThrowsAccessDenied() {
        authenticateAs(patientB);

        LabResult released = LabResult.builder()
                .id("res-1")
                .patientId("pat-user-A")
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .build();

        assertFalse(authorizationService.canAccessLabResult(released));
        assertThrows(AccessDeniedException.class, () -> authorizationService.checkLabResultAccess(released));
    }

    @Test
    @DisplayName("E3: Patient cannot view DRAFT or unverified results")
    void patientA_ViewDraftResult_ThrowsAccessDenied() {
        authenticateAs(patientA);

        LabResult draft = LabResult.builder()
                .id("res-draft")
                .patientId("pat-user-A")
                .workflowStatus(LabResultWorkflowStatus.DRAFT)
                .build();

        assertFalse(authorizationService.canAccessLabResult(draft));
        assertThrows(AccessDeniedException.class, () -> authorizationService.checkLabResultAccess(draft));

        LabResult entered = LabResult.builder()
                .id("res-entered")
                .patientId("pat-user-A")
                .workflowStatus(LabResultWorkflowStatus.ENTERED)
                .build();

        assertFalse(authorizationService.canAccessLabResult(entered));
    }

    @Test
    @DisplayName("E4: Patient cannot view DRAFT lab order")
    void patientA_ViewDraftOrder_ThrowsAccessDenied() {
        authenticateAs(patientA);

        LabOrder draftOrder = LabOrder.builder()
                .id("ord-draft")
                .patientId("pat-user-A")
                .status(LabOrderStatus.DRAFT)
                .build();

        assertFalse(authorizationService.canAccessLabOrder(draftOrder));
        assertThrows(AccessDeniedException.class, () -> authorizationService.checkLabOrderAccess(draftOrder));
    }

    @Test
    @DisplayName("E5: Doctor with clinical relationship can access lab order")
    void doctorA_WithClinicalRelationship_CanAccessLabOrder() {
        authenticateAs(doctorA);

        LabOrder order = LabOrder.builder()
                .id("ord-1")
                .doctorId("doc-user-A")
                .patientId("pat-user-A")
                .status(LabOrderStatus.ORDERED)
                .build();

        assertTrue(authorizationService.canAccessLabOrder(order));
    }

    @Test
    @DisplayName("E6: Lab technician cannot sign doctor clinical encounters")
    void labTech_CannotSignClinicalEncounter() {
        authenticateAs(labTech);

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .doctorId("doc-user-A")
                .patientId("pat-user-A")
                .build();

        assertThrows(AccessDeniedException.class, () -> authorizationService.checkEncounterSigning(encounter));
    }

    @Test
    @DisplayName("E7: Lab technician can enter results but cannot verify or release them")
    void labTech_Permissions_Enforced() {
        authenticateAs(labTech);

        assertTrue(authorizationService.canEnterLabResult());
        assertFalse(authorizationService.canVerifyOrReleaseLabResult());
    }

    @Test
    @DisplayName("E8: Doctor and Admin can verify and release results")
    void doctorAndAdmin_CanVerifyOrRelease() {
        authenticateAs(doctorA);
        assertTrue(authorizationService.canVerifyOrReleaseLabResult());

        authenticateAs(admin);
        assertTrue(authorizationService.canVerifyOrReleaseLabResult());
    }
}
