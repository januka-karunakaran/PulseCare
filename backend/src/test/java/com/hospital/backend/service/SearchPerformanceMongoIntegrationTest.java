package com.hospital.backend.service;

import com.hospital.backend.dto.*;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Search Performance & Scale Integration Test.
 * PRIORITY P0 - MANDATORY.
 * Uses an isolated, disposable Testcontainers MongoDB container.
 * Fail-Closed: Never connects or falls back to local or shared databases.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
public class SearchPerformanceMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_search_perf_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                mongoDBContainer.start();
            }
        } catch (Throwable ignored) {
        }
    }

    @DynamicPropertySource
    static void setMongoProperties(DynamicPropertyRegistry registry) {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        } else {
            registry.add("spring.data.mongodb.uri", () -> "mongodb://fail-closed-docker-required:27017/" + ISOLATED_TEST_DB);
            registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
        }
    }

    @BeforeAll
    public static void verifyDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable() || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED TEST ISOLATION: Docker is required for SearchPerformanceMongoIntegrationTest.");
        }
    }

    @AfterAll
    public static void stopContainer() {
        if (mongoDBContainer != null && mongoDBContainer.isRunning()) {
            try {
                mongoDBContainer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private AuthorizationService authz;

    private SearchServiceImpl searchService;

    @BeforeEach
    public void setup() {
        if (mongoDBContainer == null || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED SAFETY GUARD: Testcontainers MongoDB instance not running.");
        }

        mongoTemplate.remove(new Query(), User.class);
        mongoTemplate.remove(new Query(), Doctor.class);
        mongoTemplate.remove(new Query(), Patient.class);
        mongoTemplate.remove(new Query(), Appointment.class);
        mongoTemplate.remove(new Query(), Prescription.class);

        searchService = new SearchServiceImpl(
                userRepository, doctorRepository, patientRepository,
                appointmentRepository, prescriptionRepository, authz, mongoTemplate
        );
    }

    @Test
    public void verifyDoctorSearch_DatabaseLevelPaginationAndFiltering() {
        // Seed 100 synthetic doctors with separate Doctor.id != Doctor.userId
        List<User> users = new ArrayList<>();
        List<Doctor> doctors = new ArrayList<>();
        List<Appointment> appts = new ArrayList<>();

        YearMonth now = YearMonth.now();
        String currentMonthDate = now.atDay(10).toString();

        for (int i = 0; i < 100; i++) {
            String spec = (i % 2 == 0) ? "Cardiology" : "Neurology";
            double rating = 3.0 + (i % 20) * 0.1; // 3.0 to 4.9
            String userId = "user-doc-" + i;
            String profileId = "doc-profile-" + i;

            User docUser = User.builder()
                    .id(userId)
                    .fullName("Doctor Number " + i)
                    .email("doctor" + i + "@hospital.com")
                    .specialization(spec)
                    .role(Role.DOCTOR)
                    .build();
            users.add(docUser);

            Doctor docProfile = Doctor.builder()
                    .id(profileId)
                    .userId(userId)
                    .fullName("Doctor Number " + i)
                    .specialization(spec)
                    .rating(rating)
                    .totalRatings(10 + i)
                    .build();
            doctors.add(docProfile);

            // Add appointments referencing both profileId and userId for doctor 0
            if (i == 0) {
                appts.add(Appointment.builder().id("appt-p1").doctorId(profileId).appointmentDate(currentMonthDate).status("CONFIRMED").build());
                appts.add(Appointment.builder().id("appt-u1").doctorId(userId).appointmentDate(currentMonthDate).status("CONFIRMED").build());
            }
        }
        userRepository.saveAll(users);
        doctorRepository.saveAll(doctors);
        appointmentRepository.saveAll(appts);

        // Search with Cardiology specialization and minRating >= 4.0
        SearchDoctorRequest request = SearchDoctorRequest.builder()
                .specialization("Cardiology")
                .minRating(4.0)
                .limit(10)
                .offset(0)
                .sortBy("rating")
                .sortOrder("desc")
                .build();

        long startTime = System.currentTimeMillis();
        SearchResultsWrapper<SearchDoctorResponse> result = searchService.searchDoctors(request);
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertTrue(result.getTotalCount() > 0, "Should match Cardiology doctors with rating >= 4.0");
        assertTrue(result.getData().size() <= 10, "Returned slice within limit");

        for (SearchDoctorResponse r : result.getData()) {
            assertEquals("Cardiology", r.getSpecialization());
            assertTrue(r.getAvgRating() >= 4.0, "Rating must satisfy minRating filter");
            assertTrue(r.getDoctorId().startsWith("doc-profile-"), "Canonical doctor profile ID must be used");
            assertNull(r.getPhoneNumber(), "Doctor phone number must be protected");
            assertNull(r.getLicenseNumber(), "Doctor license number must be protected");
        }

        // Check appointment aggregation on doctor 0 if queried directly
        SearchDoctorRequest doc0Request = SearchDoctorRequest.builder()
                .searchTerm("Doctor Number 0")
                .build();
        SearchResultsWrapper<SearchDoctorResponse> doc0Result = searchService.searchDoctors(doc0Request);
        assertEquals(1, doc0Result.getTotalCount());
        SearchDoctorResponse doc0 = doc0Result.getData().get(0);
        // Both profileId and userId appointments summed: 1 + 1 = 2
        assertEquals(2, doc0.getTotalAppointments());
        assertEquals(2L, doc0.getAppointmentsThisMonth());
    }

    @Test
    public void verifyAppointmentFiltering_DatabaseLevelDateRangeAndStatus() {
        // Seed 200 appointments
        List<Appointment> appts = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String status = (i % 2 == 0) ? "CONFIRMED" : "CANCELLED";
            String date = String.format("2026-11-%02d", (i % 28) + 1);
            appts.add(Appointment.builder()
                    .id("appt-" + i)
                    .doctorId("doc-1")
                    .patientId("patient-1")
                    .appointmentDate(date)
                    .appointmentTime("10:00 AM")
                    .status(status)
                    .reason("Checkup " + i)
                    .build());
        }
        appointmentRepository.saveAll(appts);

        // Query for status CONFIRMED between Nov 10 and Nov 20
        Query query = new Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is("CONFIRMED")
                .and("appointmentDate").gte("2026-11-10").lte("2026-11-20"));

        long count = mongoTemplate.count(query, Appointment.class);
        assertTrue(count > 0);

        List<Appointment> results = mongoTemplate.find(query.skip(0).limit(5), Appointment.class);
        assertEquals(5, results.size());
        for (Appointment a : results) {
            assertEquals("CONFIRMED", a.getStatus());
            assertTrue(a.getAppointmentDate().compareTo("2026-11-10") >= 0);
            assertTrue(a.getAppointmentDate().compareTo("2026-11-20") <= 0);
        }
    }

    @Test
    public void verifyPrescriptionMedicineSearch_MultikeyIndex() {
        List<Prescription> prescs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            List<String> meds = (i % 2 == 0)
                    ? List.of("Amoxicillin 500mg", "Ibuprofen")
                    : List.of("Metformin 850mg", "Atorvastatin");

            prescs.add(Prescription.builder()
                    .id("presc-" + i)
                    .doctorId("doc-1")
                    .patientId("pat-1")
                    .medicines(meds)
                    .createdAt("2026-11-01T10:00:00")
                    .build());
        }
        prescriptionRepository.saveAll(prescs);

        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("medicines").regex("Amoxicillin", "i"));
        long count = mongoTemplate.count(query, Prescription.class);
        assertEquals(25, count, "Exactly 25 prescriptions contain Amoxicillin");

        List<Prescription> matched = mongoTemplate.find(query.limit(5), Prescription.class);
        assertEquals(5, matched.size());
        for (Prescription p : matched) {
            assertTrue(p.getMedicines().stream().anyMatch(m -> m.contains("Amoxicillin")));
        }
    }
}
