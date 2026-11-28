package com.hospital.backend.security;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.*;
import com.hospital.backend.service.BillingService;
import com.hospital.backend.service.impl.AdmissionServiceImpl;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BedSecurityAuthorizationTest {

    @Mock
    private BedBookingRepository bedBookingRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private HospitalWardRepository wardRepository;

    @Mock
    private BedOccupancyEventRepository eventRepository;

    @Mock
    private BedTransferRecordRepository transferRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private ClinicalEncounterRepository clinicalEncounterRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AuthorizationService authz;

    @Mock
    private BillingService billingService;

    @InjectMocks
    private AdmissionServiceImpl admissionService;

    private User patientUserA;
    private User patientUserB;
    private User labTechUser;
    private User pharmacistUser;
    private User doctorUser;
    private User adminUser;

    private Patient patientA;
    private Patient patientB;

    @BeforeEach
    void setUp() {
        patientUserA = User.builder().id("usr-pat-a").email("pat_a@test.com").role(Role.PATIENT).fullName("Patient A").build();
        patientUserB = User.builder().id("usr-pat-b").email("pat_b@test.com").role(Role.PATIENT).fullName("Patient B").build();
        labTechUser = User.builder().id("usr-lab").email("lab@test.com").role(Role.LAB_TECH).fullName("Lab Tech").build();
        pharmacistUser = User.builder().id("usr-pharm").email("pharm@test.com").role(Role.PHARMACIST).fullName("Pharmacist").build();
        doctorUser = User.builder().id("usr-doc").email("doc@test.com").role(Role.DOCTOR).fullName("Doctor").build();
        adminUser = User.builder().id("usr-admin").email("admin@test.com").role(Role.ADMIN).fullName("Admin").build();

        patientA = Patient.builder().id("pat-a").userId(patientUserA.getId()).fullName("Patient A").build();
        patientB = Patient.builder().id("pat-b").userId(patientUserB.getId()).fullName("Patient B").build();

        when(patientRepository.findById("pat-a")).thenReturn(Optional.of(patientA));
        when(patientRepository.findById("pat-b")).thenReturn(Optional.of(patientB));
    }

    @Test
    @DisplayName("H1: Patient A cannot access admissions of Patient B (IDOR prevention)")
    void testPatientCannotAccessOtherPatientAdmissions() {
        when(authz.getCurrentUser()).thenReturn(patientUserA);
        when(authz.canAccessPatient("pat-b")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            admissionService.getPatientAdmissions("pat-b");
        });
    }

    @Test
    @DisplayName("H4 & H5: Non-clinical roles (LAB_TECH, PHARMACIST) denied patient admission operations")
    void testNonClinicalRolesDeniedAdmissions() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canAccessPatient("pat-a")).thenReturn(true);

        AdmissionRequestDTO request = AdmissionRequestDTO.builder()
                .patientId("pat-a")
                .bedId("bed-1")
                .build();

        assertThrows(AccessDeniedException.class, () -> {
            admissionService.admitPatient(request);
        });

        when(authz.getCurrentUser()).thenReturn(pharmacistUser);
        assertThrows(AccessDeniedException.class, () -> {
            admissionService.admitPatient(request);
        });
    }

    @Test
    @DisplayName("H8: Patient cannot access raw bed occupancy history")
    void testPatientCannotAccessBedOccupancyHistory() {
        when(authz.getCurrentUser()).thenReturn(patientUserA);

        assertThrows(AccessDeniedException.class, () -> {
            admissionService.getBedOccupancyHistory("bed-1");
        });
    }

    @Test
    @DisplayName("H9: Admin cannot impersonate physician to certify clinical discharge readiness")
    void testAdminCannotCertifyClinicalDischargeReadiness() {
        when(authz.getCurrentUser()).thenReturn(adminUser);
        BedBooking booking = BedBooking.builder()
                .id("booking-1")
                .patientId("pat-a")
                .bedId("bed-1")
                .status("ADMITTED")
                .build();
        when(bedBookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> {
            admissionService.prepareDischarge("booking-1", new com.hospital.backend.dto.bed.DischargeRequestDTO());
        });
    }

    @Test
    @DisplayName("H10: Doctor without clinical relationship cannot certify clinical discharge readiness")
    void testDoctorWithoutRelationshipCannotCertifyDischarge() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        BedBooking booking = BedBooking.builder()
                .id("booking-1")
                .patientId("pat-a")
                .bedId("bed-1")
                .status("ADMITTED")
                .build();
        when(bedBookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(authz.hasDoctorPatientRelationship(doctorUser.getId(), "pat-a")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            admissionService.prepareDischarge("booking-1", new com.hospital.backend.dto.bed.DischargeRequestDTO());
        });
    }

    @Test
    @DisplayName("H11: Discharge rejected if admission is not clinically cleared (DISCHARGE_READY)")
    void testDischargeRejectedIfNotClinicallyCleared() {
        when(authz.getCurrentUser()).thenReturn(adminUser);
        BedBooking booking = BedBooking.builder()
                .id("booking-1")
                .patientId("pat-a")
                .bedId("bed-1")
                .status("ADMITTED")
                .build();
        when(bedBookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThrows(com.hospital.backend.exception.InvalidBookingTransitionException.class, () -> {
            admissionService.dischargePatient("booking-1", new com.hospital.backend.dto.bed.DischargeRequestDTO());
        });
    }
}
