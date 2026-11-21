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
        // Seed 100 synthetic doctors
        List<User> users = new ArrayList<>();
        List<Doctor> doctors = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String spec = (i % 2 == 0) ? "Cardiology" : "Neurology";
            double rating = 3.0 + (i % 20) * 0.1;
            User docUser = User.builder()
                    .id("user-doc-" + i)
                    .fullName("Doctor Number " + i)
                    .email("doctor" + i + "@hospital.com")
                    .specialization(spec)
                    .role(Role.DOCTOR)
                    .build();
            users.add(docUser);

            Doctor docProfile = Doctor.builder()
                    .id("doc-profile-" + i)
                    .userId("user-doc-" + i)
                    .fullName("Doctor Number " + i)
                    .specialization(spec)
                    .rating(rating)
                    .totalRatings(10 + i)
                    .build();
            doctors.add(docProfile);
        }
        userRepository.saveAll(users);
        doctorRepository.saveAll(doctors);

        SearchDoctorRequest request = SearchDoctorRequest.builder()
                .specialization("Cardiology")
                .limit(10)
                .offset(0)
                .sortBy("name")
                .sortOrder("asc")
                .build();

        long startTime = System.currentTimeMillis();
        SearchResultsWrapper<SearchDoctorResponse> result = searchService.searchDoctors(request);
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertEquals(50, result.getTotalCount(), "Exactly 50 Cardiology doctors in dataset");
        assertEquals(10, result.getData().size(), "Only requested limit of 10 items returned");
        assertTrue(result.getHasMore());
        assertEquals(5, result.getTotalPages());
        assertTrue(elapsed < 1000, "Database-side paginated query must execute in under 1 second");
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
}
