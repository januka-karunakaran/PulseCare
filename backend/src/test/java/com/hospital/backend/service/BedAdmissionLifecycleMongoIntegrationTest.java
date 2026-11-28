package com.hospital.backend.service;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.dto.bed.AdmissionResponseDTO;
import com.hospital.backend.dto.bed.DischargeRequestDTO;
import com.hospital.backend.dto.bed.TransferRequestDTO;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class BedAdmissionLifecycleMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_bed_lifecycle_" + UUID.randomUUID().toString().replace("-", "");

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
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl(ISOLATED_TEST_DB));
        registry.add("spring.data.mongodb.database", () -> ISOLATED_TEST_DB);
    }

    @BeforeAll
    public static void verifyIsolatedEnvironment() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            fail("FAIL-CLOSED: Docker is mandatory for BedAdmissionLifecycleMongoIntegrationTest. Connecting to non-isolated environments is prohibited.");
        }
        assertTrue(mongoDBContainer.isRunning(),
                "FAIL-CLOSED: Disposable Testcontainers MongoDB instance must be active");
    }

    @Autowired
    private AdmissionService admissionService;

    @Autowired
    private BedService bedService;

    @Autowired
    private WardService wardService;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedBookingRepository bedBookingRepository;

    @Autowired
    private HospitalWardRepository wardRepository;

    @Autowired
    private BedOccupancyEventRepository eventRepository;

    @Autowired
    private BedTransferRecordRepository transferRepository;

    @Autowired
    private BillingChargeRepository billingChargeRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User adminUser;
    private User doctorUser;
    private Doctor doctor;
    private Patient patient1;
    private Patient patient2;
    private HospitalWard generalWard;
    private HospitalWard icuWard;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        // Enforce DB Isolation Guard: Ensure we NEVER touch any database other than our disposable test DB
        String connectedDb = mongoTemplate.getDb().getName();
        assertTrue(connectedDb.startsWith("disposable_bed_lifecycle_"),
                "SECURITY GUARD: Refusing operations on non-isolated database: " + connectedDb);
        assertEquals(ISOLATED_TEST_DB, connectedDb);

        bedBookingRepository.deleteAll();
        bedRepository.deleteAll();
        wardRepository.deleteAll();
        eventRepository.deleteAll();
        transferRepository.deleteAll();
        billingChargeRepository.deleteAll();

        adminUser = userRepository.findByEmail("admin_inpatient@test.com")
                .orElseGet(() -> userRepository.save(User.builder().email("admin_inpatient@test.com").password("pass").role(Role.ADMIN).fullName("Admin").build()));

        doctorUser = userRepository.findByEmail("doc_inpatient@test.com")
                .orElseGet(() -> userRepository.save(User.builder().email("doc_inpatient@test.com").password("pass").role(Role.DOCTOR).fullName("Dr. Smith").build()));

        doctor = doctorRepository.findByUserId(doctorUser.getId())
                .orElseGet(() -> doctorRepository.save(Doctor.builder().userId(doctorUser.getId()).fullName("Dr. Smith").specialization("Critical Care").build()));

        patient1 = patientRepository.findById("pat_inp_01")
                .orElseGet(() -> patientRepository.save(Patient.builder().id("pat_inp_01").fullName("Alice").build()));

        patient2 = patientRepository.findById("pat_inp_02")
                .orElseGet(() -> patientRepository.save(Patient.builder().id("pat_inp_02").fullName("Bob").build()));

        authenticateAs(adminUser);

        // Establish doctor-patient relationship for clinical discharge authorization
        appointmentRepository.save(Appointment.builder()
                .doctorId(doctor.getId())
                .patientId(patient1.getId())
                .status("COMPLETED")
                .appointmentDate(java.time.LocalDate.now().toString())
                .build());

        generalWard = wardService.createWard(HospitalWard.builder().wardCode("WARD-GEN-01").wardName("General Ward 1").wardType("GENERAL").build());
        icuWard = wardService.createWard(HospitalWard.builder().wardCode("WARD-ICU-01").wardName("ICU Ward 1").wardType("ICU").build());
    }

    private void authenticateAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("GROUP A: Ward and Bed validation - creation and duplicate prevention")
    void testWardAndBedValidation() {
        // A2: Duplicate ward code rejected
        assertThrows(DuplicateResourceException.class, () -> {
            wardService.createWard(HospitalWard.builder().wardCode("WARD-GEN-01").wardName("Duplicate General").build());
        });

        // A3: Add bed
        Bed bed = bedService.addBed(Bed.builder()
                .bedNumber("B101")
                .ward("General Ward 1")
                .wardId(generalWard.getId())
                .type("GENERAL")
                .pricePerDay(1000.0)
                .build());
        assertNotNull(bed.getId());
        assertEquals("AVAILABLE", bed.getStatus());

        // A4: Duplicate bed number in same ward rejected
        assertThrows(DuplicateResourceException.class, () -> {
            bedService.addBed(Bed.builder().bedNumber("B101").ward("General Ward 1").build());
        });
    }

    @Test
    @DisplayName("GROUP B: Patient Admission Workflow and Duplicate Prevention")
    void testPatientAdmissionLifecycle() {
        Bed bed = bedService.addBed(Bed.builder().bedNumber("B102").ward("General Ward 1").wardId(generalWard.getId()).type("GENERAL").build());

        AdmissionRequestDTO request = AdmissionRequestDTO.builder()
                .patientId(patient1.getId())
                .doctorId(doctor.getId())
                .bedId(bed.getId())
                .admissionSource("ELECTIVE")
                .reasonCode("POST_OP")
                .notes("Standard recovery observation")
                .build();

        AdmissionResponseDTO response = admissionService.admitPatient(request);
        assertNotNull(response.getId());
        assertNotNull(response.getAdmissionNumber());
        assertEquals("ADMITTED", response.getStatus());

        // Verify Bed is now OCCUPIED and linked
        Bed updatedBed = bedRepository.findById(bed.getId()).orElseThrow();
        assertEquals("OCCUPIED", updatedBed.getStatus());
        assertEquals(response.getId(), updatedBed.getCurrentBookingId());
        assertEquals(patient1.getId(), updatedBed.getCurrentPatientId());

        // B6: Prevent duplicate active admission for same patient
        Bed anotherBed = bedService.addBed(Bed.builder().bedNumber("B103").ward("General Ward 1").wardId(generalWard.getId()).type("GENERAL").build());
        assertThrows(InvalidBookingTransitionException.class, () -> {
            admissionService.admitPatient(AdmissionRequestDTO.builder()
                    .patientId(patient1.getId())
                    .bedId(anotherBed.getId())
                    .build());
        });

        // B4: Occupied bed cannot be claimed by another patient
        assertThrows(BedAlreadyOccupiedException.class, () -> {
            admissionService.admitPatient(AdmissionRequestDTO.builder()
                    .patientId(patient2.getId())
                    .bedId(bed.getId())
                    .build());
        });
    }

    @Test
    @DisplayName("GROUP C: MANDATORY CONCURRENCY SCENARIO - 20 concurrent requests competing for 1 AVAILABLE bed")
    void testMandatoryConcurrencyScenario_20RequestsCompetingFor1Bed() throws Exception {
        Bed contestedBed = bedService.addBed(Bed.builder()
                .bedNumber("B-RACE-01")
                .ward("General Ward 1")
                .wardId(generalWard.getId())
                .type("GENERAL")
                .pricePerDay(1200.0)
                .build());

        // Create 20 distinct synthetic patients
        int threadCount = 20;
        List<Patient> testPatients = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Patient p = patientRepository.save(Patient.builder()
                    .id("pat_race_" + i)
                    .fullName("Racer Patient " + i)
                    .build());
            testPatients.add(p);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<String> successfulAdmissionIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final Patient p = testPatients.get(i);
            executor.submit(() -> {
                authenticateAs(adminUser);
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronize sudden-burst concurrency
                    AdmissionResponseDTO res = admissionService.admitPatient(AdmissionRequestDTO.builder()
                            .patientId(p.getId())
                            .bedId(contestedBed.getId())
                            .doctorId(doctor.getId())
                            .admissionSource("EMERGENCY")
                            .reasonCode("CONCURRENCY_TEST")
                            .build());
                    if (res != null) {
                        successCount.incrementAndGet();
                        successfulAdmissionIds.add(res.getId());
                    }
                } catch (BedAlreadyOccupiedException ex) {
                    conflictCount.incrementAndGet();
                } catch (org.springframework.dao.DataAccessException ex) {
                    // Under multi-document transactions, concurrent writes on the contested bed document
                    // throw TransientDataAccessException / MongoCommandException (WriteConflict 112)
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 20 threads simultaneously!
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        // MANDATORY INVARIANTS:
        // Exactly 1 success, exactly 19 conflict responses
        assertEquals(1, successCount.get(), "CONCURRENCY INVARIANT: Exactly 1 admission must succeed");
        assertEquals(19, conflictCount.get(), "CONCURRENCY INVARIANT: Exactly 19 requests must receive conflict error");

        Bed finalBedState = bedRepository.findById(contestedBed.getId()).orElseThrow();
        assertEquals("OCCUPIED", finalBedState.getStatus());
        assertEquals(successfulAdmissionIds.get(0), finalBedState.getCurrentBookingId());

        // Invariant: Exactly 1 active booking in DB
        List<BedBooking> activeBookings = bedBookingRepository.findByStatusIn(List.of("ACTIVE", "ADMITTED"));
        assertEquals(1, activeBookings.size());
        assertEquals(successfulAdmissionIds.get(0), activeBookings.get(0).getId());

        // Invariant: Exactly 1 admission occupancy event for this bed
        List<BedOccupancyEvent> events = eventRepository.findByBedIdOrderByOccurredAtAsc(contestedBed.getId());
        assertEquals(1, events.size());
        assertEquals("BED_OCCUPIED", events.get(0).getEventType());
    }

    @Test
    @DisplayName("GROUP D & E: Patient Transfer, Safe Discharge, and Bed Turnover Cleaning Workflow")
    void testTransferAndDischargeWorkflow() {
        Bed sourceBed = bedService.addBed(Bed.builder().bedNumber("GEN-201").ward("General Ward 1").wardId(generalWard.getId()).type("GENERAL").build());
        Bed destBed = bedService.addBed(Bed.builder().bedNumber("ICU-201").ward("ICU Ward 1").wardId(icuWard.getId()).type("ICU").build());

        // 1. Initial Admission
        AdmissionResponseDTO admission = admissionService.admitPatient(AdmissionRequestDTO.builder()
                .patientId(patient1.getId())
                .doctorId(doctor.getId())
                .bedId(sourceBed.getId())
                .build());

        assertEquals("OCCUPIED", bedRepository.findById(sourceBed.getId()).orElseThrow().getStatus());
        assertEquals("AVAILABLE", bedRepository.findById(destBed.getId()).orElseThrow().getStatus());

        // 2. Transfer from General Ward to ICU
        BedTransferRecord transfer = admissionService.transferBed(admission.getId(), TransferRequestDTO.builder()
                .destinationBedId(destBed.getId())
                .reasonCode("CLINICAL_ESCALATION")
                .notes("Transferred to ICU due to vital signs decline")
                .build());

        assertNotNull(transfer);
        assertEquals(sourceBed.getId(), transfer.getSourceBedId());
        assertEquals(destBed.getId(), transfer.getDestinationBedId());

        // Verify Source Bed transitioned to CLEANING turnover
        Bed updatedSource = bedRepository.findById(sourceBed.getId()).orElseThrow();
        assertEquals("CLEANING", updatedSource.getStatus(), "Source bed must enter CLEANING turnover after patient transfer");
        assertNull(updatedSource.getCurrentBookingId());

        // Verify Destination Bed is now OCCUPIED by patient
        Bed updatedDest = bedRepository.findById(destBed.getId()).orElseThrow();
        assertEquals("OCCUPIED", updatedDest.getStatus());
        assertEquals(admission.getId(), updatedDest.getCurrentBookingId());

        // 3. Mark Source Bed ready after cleaning
        Bed cleanedBed = admissionService.markBedReady(sourceBed.getId());
        assertEquals("AVAILABLE", cleanedBed.getStatus());

        // 4. Clinical Discharge Readiness (must be performed by attending Doctor)
        authenticateAs(doctorUser);
        admissionService.prepareDischarge(admission.getId(), new DischargeRequestDTO());
        BedBooking readyBooking = bedBookingRepository.findById(admission.getId()).orElseThrow();
        assertEquals("DISCHARGE_READY", readyBooking.getStatus());

        // 5. Final Discharge (performed by Admin or Bed Manager or Doctor)
        authenticateAs(adminUser);
        AdmissionResponseDTO dischargeResponse = admissionService.dischargePatient(admission.getId(), new DischargeRequestDTO());
        assertEquals("DISCHARGED", dischargeResponse.getStatus());

        // Verify Destination Bed entered CLEANING turnover
        Bed dischargedBed = bedRepository.findById(destBed.getId()).orElseThrow();
        assertEquals("CLEANING", dischargedBed.getStatus(), "Bed must enter CLEANING turnover after discharge");
        assertNull(dischargedBed.getCurrentBookingId());

        // 6. Verify Billing Integration: Bed Stay charge was recorded
        List<BillingCharge> charges = billingChargeRepository.findByPatientId(patient1.getId());
        assertFalse(charges.isEmpty(), "Billing charge must be registered for patient bed stay");
        assertTrue(charges.stream().anyMatch(c -> "BED_STAY".equals(c.getSourceType().name())));

        // 7. Verify Bed Occupancy Events Sequence
        List<BedOccupancyEvent> admissionEvents = eventRepository.findByAdmissionIdOrderBySequenceNumberAsc(admission.getId());
        assertFalse(admissionEvents.isEmpty());
        assertTrue(admissionEvents.stream().anyMatch(e -> "BED_OCCUPIED".equals(e.getEventType())));
        assertTrue(admissionEvents.stream().anyMatch(e -> "TRANSFER_COMPLETED".equals(e.getEventType())));
        assertTrue(admissionEvents.stream().anyMatch(e -> "DISCHARGE_COMPLETED".equals(e.getEventType())));
    }
}
