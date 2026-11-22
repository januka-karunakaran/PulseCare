package com.hospital.backend.security;

import com.hospital.backend.enums.EncounterStatus;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalAuthorizationTest {

    @Mock private UserRepository userRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PrescriptionRepository prescriptionRepository;

    @InjectMocks
    private AuthorizationService authz;

    private User doctorA;
    private User doctorB;
    private User patientA;
    private User patientB;
    private User admin;

    private Doctor doctorDocA;
    private Patient patientDocA;

    @BeforeEach
    void setUp() {
        doctorA = User.builder().id("doc-user-A").email("docA@hospital.org").role(Role.DOCTOR).fullName("Dr. A").build();
        doctorB = User.builder().id("doc-user-B").email("docB@hospital.org").role(Role.DOCTOR).fullName("Dr. B").build();
        patientA = User.builder().id("pat-user-A").email("patientA@email.com").role(Role.PATIENT).fullName("Patient A").build();
        patientB = User.builder().id("pat-user-B").email("patientB@email.com").role(Role.PATIENT).fullName("Patient B").build();
        admin = User.builder().id("admin-user-1").email("admin@hospital.org").role(Role.ADMIN).fullName("Admin").build();

        doctorDocA = Doctor.builder().id("doc-rec-A").userId("doc-user-A").fullName("Dr. A").build();
        patientDocA = Patient.builder().id("pat-rec-A").userId("pat-user-A").fullName("Patient A").build();
    }

    private void authenticateAs(User user) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getName()).thenReturn(user.getEmail());

        SecurityContext context = mock(SecurityContext.class);
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Patient can access their own finalized encounter")
    void testPatientCanAccessOwnFinalizedEncounter() {
        authenticateAs(patientA);
        when(patientRepository.findById("pat-rec-A")).thenReturn(Optional.of(patientDocA));

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.SIGNED)
                .build();

        assertThat(authz.canAccessEncounter(encounter)).isTrue();
    }

    @Test
    @DisplayName("Patient CANNOT access an in-progress or draft encounter")
    void testPatientCannotAccessDraftEncounter() {
        authenticateAs(patientA);

        ClinicalEncounter draftEncounter = ClinicalEncounter.builder()
                .id("enc-draft")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        assertThat(authz.canAccessEncounter(draftEncounter)).isFalse();
    }

    @Test
    @DisplayName("Patient B cannot access Patient A's finalized encounter (Cross-patient IDOR prevented)")
    void testCrossPatientAccessDenied() {
        authenticateAs(patientB);
        when(patientRepository.findById("pat-rec-A")).thenReturn(Optional.of(patientDocA));

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.SIGNED)
                .build();

        assertThat(authz.canAccessEncounter(encounter)).isFalse();
    }

    @Test
    @DisplayName("Assigned Doctor A can access encounter")
    void testAssignedDoctorCanAccessEncounter() {
        authenticateAs(doctorA);
        when(doctorRepository.findById("doc-rec-A")).thenReturn(Optional.of(doctorDocA));

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        assertThat(authz.canAccessEncounter(encounter)).isTrue();
    }

    @Test
    @DisplayName("Unrelated Doctor B is denied access to Doctor A's encounter")
    void testUnrelatedDoctorAccessDenied() {
        authenticateAs(doctorB);
        when(doctorRepository.findById("doc-rec-A")).thenReturn(Optional.of(doctorDocA));
        when(doctorRepository.findByUserId("doc-user-B")).thenReturn(Optional.empty());

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        assertThat(authz.canAccessEncounter(encounter)).isFalse();
    }

    @Test
    @DisplayName("Patient cannot sign an encounter; checkEncounterSigning throws AccessDeniedException")
    void testPatientCannotSignInEncounter() {
        authenticateAs(patientA);

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        assertThrows(AccessDeniedException.class, () -> authz.checkEncounterSigning(encounter));
    }

    @Test
    @DisplayName("Only authorized doctor can sign; Doctor B cannot sign Doctor A's encounter")
    void testOnlyAssignedDoctorCanSign() {
        authenticateAs(doctorB);
        when(doctorRepository.findById("doc-rec-A")).thenReturn(Optional.of(doctorDocA));

        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("pat-rec-A")
                .doctorId("doc-rec-A")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        assertThrows(AccessDeniedException.class, () -> authz.checkEncounterSigning(encounter));
    }
}
