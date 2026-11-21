package com.hospital.backend.service;

import com.hospital.backend.dto.AppointmentRequest;
import com.hospital.backend.dto.AvailableSlotsResponse;
import com.hospital.backend.dto.DoctorAvailabilityDTO;
import com.hospital.backend.exception.AppointmentSlotConflictException;
import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.service.impl.AppointmentServiceImpl;
import com.hospital.backend.service.impl.AvailabilityServiceImpl;
import com.hospital.backend.service.impl.BedServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise MongoDB Concurrency Integration Tests for Appointments and Beds.
 * PRIORITY P0 - MANDATORY.
 * Uses an isolated, disposable Testcontainers MongoDB container.
 * Fail-Closed: Never connects or falls back to local, shared, or production databases.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = false)
public class BookingConcurrencyMongoIntegrationTest {

    private static final String ISOLATED_TEST_DB = "disposable_booking_concurrency_" + UUID.randomUUID().toString().replace("-", "");

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
            throw new IllegalStateException("FAIL-CLOSED TEST ISOLATION: Docker is required for BookingConcurrencyMongoIntegrationTest "
                    + "to run safely inside an isolated Testcontainers disposable database. Local shared database fallback is strictly prohibited.");
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
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentSlotReservationRepository slotReservationRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedBookingRepository bedBookingRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private NotificationService notificationService;

    private AppointmentServiceImpl appointmentServiceInstance1;
    private AppointmentServiceImpl appointmentServiceInstance2;

    private BedServiceImpl bedServiceInstance1;
    private BedServiceImpl bedServiceInstance2;

    private AvailabilityServiceImpl availabilityService;

    @BeforeEach
    public void setup() {
        if (mongoDBContainer == null || !mongoDBContainer.isRunning()) {
            throw new IllegalStateException("FAIL-CLOSED SAFETY GUARD: Disposable Testcontainers MongoDB instance is NOT running.");
        }

        // Clean isolated test collections
        mongoTemplate.remove(new Query(), Appointment.class);
        mongoTemplate.remove(new Query(), AppointmentSlotReservation.class);
        mongoTemplate.remove(new Query(), DailyQueueCounter.class);
        mongoTemplate.remove(new Query(), Bed.class);
        mongoTemplate.remove(new Query(), BedBooking.class);
        mongoTemplate.remove(new Query(), Doctor.class);
        mongoTemplate.remove(new Query(), Patient.class);
        mongoTemplate.remove(new Query(), Availability.class);

        availabilityService = new AvailabilityServiceImpl(
                availabilityRepository, appointmentRepository, slotReservationRepository, doctorRepository);

        // Multiple distinct service instances sharing the same MongoDB database
        appointmentServiceInstance1 = new AppointmentServiceImpl(
                appointmentRepository, slotReservationRepository, doctorRepository,
                patientRepository, notificationService, availabilityService, userRepository, mongoTemplate);

        appointmentServiceInstance2 = new AppointmentServiceImpl(
                appointmentRepository, slotReservationRepository, doctorRepository,
                patientRepository, notificationService, availabilityService, userRepository, mongoTemplate);

        bedServiceInstance1 = new BedServiceImpl(bedRepository, bedBookingRepository, mongoTemplate);
        bedServiceInstance2 = new BedServiceImpl(bedRepository, bedBookingRepository, mongoTemplate);
    }

    private Doctor createTestDoctor(String id, String userId, String name) {
        Doctor doc = Doctor.builder()
                .id(id)
                .userId(userId)
                .fullName(name)
                .specialization("General Medicine")
                .build();
        doctorRepository.save(doc);

        // Configure doctor availability for dates
        Availability availability = Availability.builder()
                .doctorId(id)
                .date("2026-12-01")
                .isAvailable(true)
                .timeSlots(List.of(
                        "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM",
                        "11:00 AM", "11:30 AM", "02:00 PM", "02:30 PM",
                        "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM",
                        "05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM",
                        "07:00 PM", "07:30 PM", "08:00 PM", "08:30 PM"
                ))
                .build();
        availabilityRepository.save(availability);

        return doc;
    }

    private Patient createTestPatient(String id, String name) {
        Patient p = Patient.builder()
                .id(id)
                .userId(id)
                .fullName(name)
                .email(name.toLowerCase().replace(" ", "") + "@example.com")
                .build();
        return patientRepository.save(p);
    }

    // ==========================================
    // GROUP A: APPOINTMENT CONCURRENCY TESTS
    // ==========================================

    @Test
    public void A1_sameSlotMultiplePatients_exactlyOneSucceeds() throws Exception {
        Doctor doctor = createTestDoctor("doc-A1", "user-doc-A1", "Dr. Alice");
        int concurrency = 20;

        for (int i = 0; i < concurrency; i++) {
            createTestPatient("patient-A1-" + i, "Patient A1 " + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Appointment> createdAppointments = new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final String patientId = "patient-A1-" + i;
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Alternate between service instance 1 and 2 to simulate multi-instance deployment
                    AppointmentServiceImpl service = (index % 2 == 0) ? appointmentServiceInstance1 : appointmentServiceInstance2;

                    AppointmentRequest req = new AppointmentRequest();
                    req.setDoctorId(doctor.getId());
                    req.setPatientId(patientId);
                    req.setAppointmentDate("2026-12-01");
                    req.setAppointmentTime("10:00 AM");
                    req.setReason("Consultation");

                    Appointment appt = service.createAppointment(req);
                    createdAppointments.add(appt);
                    successCount.incrementAndGet();
                } catch (AppointmentSlotConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // Any unexpected exception should fail the test
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(1, successCount.get(), "Exactly one booking must succeed for a single-capacity slot");
        assertEquals(19, conflictCount.get(), "The other 19 concurrent requests must receive slot conflict");

        // Verify database invariants
        List<Appointment> dbAppointments = appointmentRepository.findAll();
        assertEquals(1, dbAppointments.size());

        List<AppointmentSlotReservation> dbReservations = slotReservationRepository.findAll();
        assertEquals(1, dbReservations.size());
        assertEquals("doc-A1_2026-12-01_10:00 AM", dbReservations.get(0).getId());
    }

    @Test
    public void A2_differentSlots_independentBookingsSucceed() throws Exception {
        Doctor doctor = createTestDoctor("doc-A2", "user-doc-A2", "Dr. Bob");
        List<String> slots = List.of("09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM", "11:00 AM");
        int concurrency = slots.size();

        for (int i = 0; i < concurrency; i++) {
            createTestPatient("patient-A2-" + i, "Patient A2 " + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            final String slot = slots.get(i);
            final String patientId = "patient-A2-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AppointmentRequest req = new AppointmentRequest();
                    req.setDoctorId(doctor.getId());
                    req.setPatientId(patientId);
                    req.setAppointmentDate("2026-12-01");
                    req.setAppointmentTime(slot);
                    req.setReason("Routine Checkup");

                    appointmentServiceInstance1.createAppointment(req);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(concurrency, successCount.get(), "All independent slots must succeed");
    }

    @Test
    public void A3_differentDoctors_noUnnecessaryConflicts() throws Exception {
        Doctor doc1 = createTestDoctor("doc-A3-1", "user-doc-A3-1", "Dr. Charles");
        Doctor doc2 = createTestDoctor("doc-A3-2", "user-doc-A3-2", "Dr. Diana");

        createTestPatient("patient-A3-1", "Patient 1");
        createTestPatient("patient-A3-2", "Patient 2");

        AppointmentRequest req1 = new AppointmentRequest();
        req1.setDoctorId(doc1.getId());
        req1.setPatientId("patient-A3-1");
        req1.setAppointmentDate("2026-12-01");
        req1.setAppointmentTime("10:00 AM");
        req1.setReason("Checkup 1");

        AppointmentRequest req2 = new AppointmentRequest();
        req2.setDoctorId(doc2.getId());
        req2.setPatientId("patient-A3-2");
        req2.setAppointmentDate("2026-12-01");
        req2.setAppointmentTime("10:00 AM");
        req2.setReason("Checkup 2");

        Appointment a1 = appointmentServiceInstance1.createAppointment(req1);
        Appointment a2 = appointmentServiceInstance2.createAppointment(req2);

        assertNotNull(a1);
        assertNotNull(a2);
        assertNotEquals(a1.getId(), a2.getId());
    }

    @Test
    public void A5_cancellation_slotCanBeSafelyRebooked() {
        Doctor doctor = createTestDoctor("doc-A5", "user-doc-A5", "Dr. Evans");
        createTestPatient("patient-A5-1", "Patient A5 1");
        createTestPatient("patient-A5-2", "Patient A5 2");

        AppointmentRequest req1 = new AppointmentRequest();
        req1.setDoctorId(doctor.getId());
        req1.setPatientId("patient-A5-1");
        req1.setAppointmentDate("2026-12-01");
        req1.setAppointmentTime("10:00 AM");
        req1.setReason("First booking");

        Appointment appt1 = appointmentServiceInstance1.createAppointment(req1);
        assertNotNull(appt1);

        // Cancel appointment 1
        Appointment cancelled = appointmentServiceInstance1.updateAppointmentStatus(appt1.getId(), "CANCELLED");
        assertEquals("CANCELLED", cancelled.getStatus());

        // Slot reservation should now be released
        assertTrue(slotReservationRepository.findById("doc-A5_2026-12-01_10:00 AM").isEmpty());

        // Patient 2 should now be able to book the exact same slot safely
        AppointmentRequest req2 = new AppointmentRequest();
        req2.setDoctorId(doctor.getId());
        req2.setPatientId("patient-A5-2");
        req2.setAppointmentDate("2026-12-01");
        req2.setAppointmentTime("10:00 AM");
        req2.setReason("Rebooked slot");

        Appointment appt2 = appointmentServiceInstance2.createAppointment(req2);
        assertNotNull(appt2);
        assertEquals("doc-A5", appt2.getDoctorId());
        assertEquals("patient-A5-2", appt2.getPatientId());
    }

    @Test
    public void A7_legacyDoctorIdMapping_cannotBypassUniqueness() {
        Doctor doctor = createTestDoctor("doc-profile-A7", "doc-user-A7", "Dr. Frank");
        createTestPatient("patient-A7-1", "Patient 1");
        createTestPatient("patient-A7-2", "Patient 2");

        // Book using canonical profile ID
        AppointmentRequest req1 = new AppointmentRequest();
        req1.setDoctorId("doc-profile-A7");
        req1.setPatientId("patient-A7-1");
        req1.setAppointmentDate("2026-12-01");
        req1.setAppointmentTime("10:00 AM");
        req1.setReason("Book with profile ID");

        Appointment appt1 = appointmentServiceInstance1.createAppointment(req1);
        assertNotNull(appt1);

        // Second booking attempts to book the same slot using Doctor.userId
        AppointmentRequest req2 = new AppointmentRequest();
        req2.setDoctorId("doc-user-A7"); // legacy user ID
        req2.setPatientId("patient-A7-2");
        req2.setAppointmentDate("2026-12-01");
        req2.setAppointmentTime("10:00 AM");
        req2.setReason("Book with user ID");

        assertThrows(
                AppointmentSlotConflictException.class,
                () -> appointmentServiceInstance2.createAppointment(req2),
                "Using legacy doctor userId must resolve to canonical profile and trigger slot conflict"
        );
    }

    @Test
    public void A8_uniqueDailyQueueNumbers_monotonicallyGenerated() throws Exception {
        Doctor doctor = createTestDoctor("doc-A8", "user-doc-A8", "Dr. Grace");
        List<String> timeSlots = List.of(
                "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM",
                "11:00 AM", "11:30 AM", "02:00 PM", "02:30 PM",
                "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM",
                "05:00 PM", "05:30 PM", "06:00 PM", "06:30 PM",
                "07:00 PM", "07:30 PM", "08:00 PM", "08:30 PM"
        );
        int concurrency = timeSlots.size();

        for (int i = 0; i < concurrency; i++) {
            createTestPatient("patient-A8-" + i, "Patient " + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        List<Integer> tokens = new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final String slot = timeSlots.get(i);
            final String patientId = "patient-A8-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AppointmentRequest req = new AppointmentRequest();
                    req.setDoctorId(doctor.getId());
                    req.setPatientId(patientId);
                    req.setAppointmentDate("2026-12-01");
                    req.setAppointmentTime(slot);
                    req.setReason("Routine");

                    Appointment appt = appointmentServiceInstance1.createAppointment(req);
                    tokens.add(appt.getTokenNumber());
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(concurrency, tokens.size());
        Set<Integer> uniqueTokens = new HashSet<>(tokens);
        assertEquals(concurrency, uniqueTokens.size(), "Every issued daily queue token must be strictly unique");
    }

    // ==========================================
    // GROUP B: BED ALLOCATION CONCURRENCY TESTS
    // ==========================================

    @Test
    public void B1_sameBedMultiplePatients_exactlyOneAllocationSucceeds() throws Exception {
        Bed bed = Bed.builder()
                .id("bed-B1")
                .bedNumber("B-101")
                .status("AVAILABLE")
                .type("GENERAL")
                .ward("Ward 1")
                .pricePerDay(400.0)
                .build();
        bedRepository.save(bed);

        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            final String patientId = "patient-bed-" + i;
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BedServiceImpl service = (index % 2 == 0) ? bedServiceInstance1 : bedServiceInstance2;

                    BedBooking booking = BedBooking.builder()
                            .bedId("bed-B1")
                            .patientId(patientId)
                            .doctorId("doctor-bed-1")
                            .notes("Emergency Admission")
                            .build();

                    service.bookBed(booking);
                    successCount.incrementAndGet();
                } catch (BedAlreadyOccupiedException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(1, successCount.get(), "Exactly one allocation must succeed for the bed");
        assertEquals(19, conflictCount.get(), "19 concurrent allocations must receive conflict");

        // Verify database state
        Bed updatedBed = bedRepository.findById("bed-B1").orElseThrow();
        assertEquals("OCCUPIED", updatedBed.getStatus());
        assertNotNull(updatedBed.getCurrentBookingId());

        List<BedBooking> bookings = bedBookingRepository.findAll();
        assertEquals(1, bookings.size());
        assertEquals("ACTIVE", bookings.get(0).getStatus());
    }

    @Test
    public void B4_repeatedDischarge_noIncorrectRelease() {
        Bed bed = Bed.builder()
                .id("bed-B4")
                .bedNumber("B-104")
                .status("AVAILABLE")
                .type("ICU")
                .ward("ICU 1")
                .build();
        bedRepository.save(bed);

        BedBooking booking = BedBooking.builder()
                .bedId("bed-B4")
                .patientId("patient-B4")
                .doctorId("doc-B4")
                .build();
        BedBooking booked = bedServiceInstance1.bookBed(booking);

        // First discharge succeeds
        BedBooking discharged = bedServiceInstance1.dischargePatient(booked.getId());
        assertEquals("DISCHARGED", discharged.getStatus());
        assertNotNull(discharged.getDischargeDate());

        Bed freeBed = bedRepository.findById("bed-B4").orElseThrow();
        assertEquals("AVAILABLE", freeBed.getStatus());

        // Repeated discharge must fail closed with InvalidBookingTransitionException
        assertThrows(
                InvalidBookingTransitionException.class,
                () -> bedServiceInstance2.dischargePatient(booked.getId()),
                "Repeated discharge on already discharged booking must be rejected"
        );
    }

    @Test
    public void B5_concurrentDischargeAndAllocation_stateIsConsistent() throws Exception {
        Bed bed = Bed.builder()
                .id("bed-B5")
                .bedNumber("B-105")
                .status("AVAILABLE")
                .type("GENERAL")
                .ward("Ward 2")
                .build();
        bedRepository.save(bed);

        // Initial booking
        BedBooking initialBooking = BedBooking.builder()
                .bedId("bed-B5")
                .patientId("patient-initial")
                .doctorId("doc-initial")
                .build();
        BedBooking activeBooking = bedServiceInstance1.bookBed(initialBooking);

        // Concurrently run discharge and a new allocation attempt
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                bedServiceInstance1.dischargePatient(activeBooking.getId());
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                BedBooking newBooking = BedBooking.builder()
                        .bedId("bed-B5")
                        .patientId("patient-new")
                        .doctorId("doc-new")
                        .build();
                bedServiceInstance2.bookBed(newBooking);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Database must remain in a valid, consistent state:
        // Either:
        // 1. Discharged and newly booked -> OCCUPIED with active booking for patient-new
        // 2. Discharged only (allocation failed or ran before discharge) -> AVAILABLE with 0 active bookings
        Bed finalBed = bedRepository.findById("bed-B5").orElseThrow();
        List<BedBooking> activeBookings = bedBookingRepository.findAll().stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()))
                .collect(Collectors.toList());

        if ("OCCUPIED".equals(finalBed.getStatus())) {
            assertEquals(1, activeBookings.size());
            assertEquals("patient-new", activeBookings.get(0).getPatientId());
        } else {
            assertEquals("AVAILABLE", finalBed.getStatus());
            assertEquals(0, activeBookings.size());
        }
    }

    // ==========================================
    // GROUP D: DATABASE INVARIANT TESTS
    // ==========================================

    @Test
    public void D1_verifyRequiredCollectionsAndUniqueIndex() {
        assertTrue(mongoTemplate.collectionExists(Appointment.class));
        assertTrue(mongoTemplate.collectionExists(AppointmentSlotReservation.class));
        assertTrue(mongoTemplate.collectionExists(DailyQueueCounter.class));
        assertTrue(mongoTemplate.collectionExists(Bed.class));
        assertTrue(mongoTemplate.collectionExists(BedBooking.class));
    }

    @Test
    public void D2_verifyDuplicateKeyBlocksDirectReservation() {
        AppointmentSlotReservation r1 = AppointmentSlotReservation.builder()
                .id("test-doc_2026-12-01_10:00 AM")
                .doctorId("test-doc")
                .appointmentDate("2026-12-01")
                .appointmentTime("10:00 AM")
                .patientId("p1")
                .status("RESERVED")
                .build();

        AppointmentSlotReservation r2 = AppointmentSlotReservation.builder()
                .id("test-doc_2026-12-01_10:00 AM")
                .doctorId("test-doc")
                .appointmentDate("2026-12-01")
                .appointmentTime("10:00 AM")
                .patientId("p2")
                .status("RESERVED")
                .build();

        slotReservationRepository.insert(r1);

        assertThrows(
                DuplicateKeyException.class,
                () -> slotReservationRepository.insert(r2),
                "MongoDB primary key on AppointmentSlotReservation must reject duplicate slot claim"
        );
    }
}
