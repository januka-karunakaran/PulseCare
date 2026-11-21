package com.hospital.backend.service;

import com.hospital.backend.dto.*;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest {

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

    @Mock
    private AuthorizationService authz;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SearchServiceImpl searchService;

    private User adminUser;
    private User doctorUser;
    private User patientUser;

    @BeforeEach
    public void setup() {
        adminUser = User.builder().id("admin-1").email("admin@test.com").role(Role.ADMIN).fullName("Admin User").build();
        doctorUser = User.builder().id("doc-user-1").email("doctor@test.com").role(Role.DOCTOR).fullName("Dr. House").build();
        patientUser = User.builder().id("patient-user-1").email("patient@test.com").role(Role.PATIENT).fullName("Jane Doe").build();
    }

    // ==========================================
    // 1. DOCTOR SEARCH TESTS
    // ==========================================

    @Test
    public void searchDoctors_ValidCriteria_ReturnsPaginatedResults() {
        SearchDoctorRequest request = SearchDoctorRequest.builder()
                .searchTerm("House")
                .specialization("Diagnostics")
                .limit(10)
                .offset(0)
                .build();

        User docUser = User.builder()
                .id("doc-1")
                .fullName("Dr. Gregory House")
                .specialization("Diagnostics")
                .email("house@hospital.com")
                .role(Role.DOCTOR)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(User.class))).thenReturn(List.of(docUser));
        when(doctorRepository.findAllById(any())).thenReturn(List.of(
                Doctor.builder().id("doc-1").fullName("Dr. Gregory House").rating(4.8).totalRatings(50).build()
        ));
        when(mongoTemplate.find(any(Query.class), eq(Appointment.class))).thenReturn(Collections.emptyList());

        SearchResultsWrapper<SearchDoctorResponse> result = searchService.searchDoctors(request);

        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getData().size());
        assertEquals("Dr. Gregory House", result.getData().get(0).getName());
        assertEquals(4.8, result.getData().get(0).getAvgRating());
    }

    @Test
    public void searchDoctors_InvalidLimit_ThrowsBadRequest() {
        SearchDoctorRequest reqNegative = SearchDoctorRequest.builder().limit(-5).build();
        assertThrows(BadRequestException.class, () -> searchService.searchDoctors(reqNegative));

        SearchDoctorRequest reqExcessive = SearchDoctorRequest.builder().limit(150).build();
        assertThrows(BadRequestException.class, () -> searchService.searchDoctors(reqExcessive));
    }

    @Test
    public void searchDoctors_InvalidOffset_ThrowsBadRequest() {
        SearchDoctorRequest request = SearchDoctorRequest.builder().offset(-1).build();
        assertThrows(BadRequestException.class, () -> searchService.searchDoctors(request));
    }

    @Test
    public void searchDoctors_InvalidSortField_ThrowsBadRequest() {
        SearchDoctorRequest request = SearchDoctorRequest.builder().sortBy("maliciousField").build();
        assertThrows(BadRequestException.class, () -> searchService.searchDoctors(request));
    }

    // ==========================================
    // 2. PATIENT SEARCH (SECURITY & ACCESS)
    // ==========================================

    @Test
    public void searchPatients_WhenRequestedByPatient_ThrowsAccessDenied() {
        when(authz.getCurrentUser()).thenReturn(patientUser);

        assertThrows(
                AccessDeniedException.class,
                () -> searchService.searchPatients("Alice", 20, 0),
                "Patients must strictly be prohibited from searching the patient directory"
        );
    }

    @Test
    public void searchPatients_WhenRequestedByDoctorWithoutRelationship_ReturnsEmpty() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(doctorRepository.findById("doc-user-1")).thenReturn(Optional.of(Doctor.builder().id("doc-profile-1").userId("doc-user-1").build()));

        // Doctor has no appointments or prescriptions
        when(mongoTemplate.findDistinct(any(Query.class), eq("patientId"), eq(Appointment.class), eq(String.class)))
                .thenReturn(Collections.emptyList());
        when(mongoTemplate.findDistinct(any(Query.class), eq("patientId"), eq(Prescription.class), eq(String.class)))
                .thenReturn(Collections.emptyList());

        SearchResultsWrapper<ProfileResponse> results = searchService.searchPatients("Alice", 20, 0);

        assertNotNull(results);
        assertEquals(0, results.getTotalCount());
        assertTrue(results.getData().isEmpty());
    }

    @Test
    public void searchPatients_WhenRequestedByAdmin_ReturnsMatchingPatients() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        User patient = User.builder()
                .id("patient-1")
                .fullName("Alice Wonderland")
                .email("alice@test.com")
                .role(Role.PATIENT)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(User.class))).thenReturn(List.of(patient));

        SearchResultsWrapper<ProfileResponse> results = searchService.searchPatients("Alice", 20, 0);

        assertNotNull(results);
        assertEquals(1, results.getTotalCount());
        assertEquals("Alice Wonderland", results.getData().get(0).getFullName());
    }

    // ==========================================
    // 3. APPOINTMENT FILTERING TESTS
    // ==========================================

    @Test
    public void filterAppointments_WhenRequestedByPatient_EnforcesPatientOwnership() {
        when(authz.getCurrentUser()).thenReturn(patientUser);
        when(patientRepository.findById("patient-user-1")).thenReturn(Optional.of(Patient.builder().id("p-profile-1").userId("patient-user-1").build()));

        Appointment appt = Appointment.builder()
                .id("appt-1")
                .patientId("patient-user-1")
                .doctorId("doc-1")
                .appointmentDate("2026-10-10")
                .appointmentTime("10:00 AM")
                .status("CONFIRMED")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Appointment.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Appointment.class))).thenReturn(List.of(appt));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().id("doc-1").fullName("Dr. House").build(),
                patientUser
        ));

        // Attempting to filter for someone else's patientId should be overridden/constrained by security
        AppointmentFilterRequest request = AppointmentFilterRequest.builder()
                .patientId("victim-patient-id")
                .build();

        SearchResultsWrapper<AppointmentFilterResponse> response = searchService.filterAppointments(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalCount());
        assertEquals("appt-1", response.getData().get(0).getAppointmentId());
    }

    @Test
    public void filterAppointments_InvalidDateRange_ThrowsBadRequest() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        AppointmentFilterRequest request = AppointmentFilterRequest.builder()
                .startDate("2026-12-31")
                .endDate("2026-01-01") // start after end
                .build();

        assertThrows(BadRequestException.class, () -> searchService.filterAppointments(request));
    }

    @Test
    public void filterAppointments_InvalidDateFormat_ThrowsBadRequest() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        AppointmentFilterRequest request = AppointmentFilterRequest.builder()
                .startDate("not-a-date")
                .build();

        assertThrows(BadRequestException.class, () -> searchService.filterAppointments(request));
    }

    // ==========================================
    // 4. PRESCRIPTION FILTERING TESTS
    // ==========================================

    @Test
    public void filterPrescriptions_MedicineSearch_BuildsArrayRegexQuery() {
        when(authz.getCurrentUser()).thenReturn(adminUser);

        Prescription prescription = Prescription.builder()
                .id("presc-1")
                .doctorId("doc-1")
                .patientId("patient-1")
                .medicines(List.of("Amoxicillin 500mg", "Paracetamol"))
                .createdAt("2026-11-01T10:00:00")
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Prescription.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Prescription.class))).thenReturn(List.of(prescription));
        when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());

        PrescriptionFilterRequest request = PrescriptionFilterRequest.builder()
                .medicine("Amoxicillin")
                .build();

        SearchResultsWrapper<PrescriptionFilterResponse> response = searchService.filterPrescriptions(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalCount());
        assertTrue(response.getData().get(0).getMedicines().contains("Amoxicillin"));
    }
}

