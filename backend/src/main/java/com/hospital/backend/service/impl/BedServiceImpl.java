package com.hospital.backend.service.impl;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.dto.bed.AdmissionResponseDTO;
import com.hospital.backend.dto.bed.BedSummaryDTO;
import com.hospital.backend.dto.bed.DischargeRequestDTO;
import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.repository.BedBookingRepository;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.service.AdmissionService;
import com.hospital.backend.service.BedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Enterprise Bed Service implementation with database-enforced atomic concurrency controls.
 * Supports backward-compatible constructors and delegates to AdmissionService when present.
 */
@Service
@Slf4j
public class BedServiceImpl implements BedService {

    private final BedRepository bedRepository;
    private final BedBookingRepository bedBookingRepository;
    private final MongoTemplate mongoTemplate;
    private final AdmissionService admissionService;

    // Primary constructor with AdmissionService injection
    @Autowired
    public BedServiceImpl(BedRepository bedRepository,
                          BedBookingRepository bedBookingRepository,
                          MongoTemplate mongoTemplate,
                          @Lazy AdmissionService admissionService) {
        this.bedRepository = bedRepository;
        this.bedBookingRepository = bedBookingRepository;
        this.mongoTemplate = mongoTemplate;
        this.admissionService = admissionService;
    }

    // Overloaded constructor for legacy tests and manual instantiation
    public BedServiceImpl(BedRepository bedRepository,
                          BedBookingRepository bedBookingRepository,
                          MongoTemplate mongoTemplate) {
        this(bedRepository, bedBookingRepository, mongoTemplate, null);
    }

    @Override
    public Bed addBed(Bed bed) {
        if (bed.getBedNumber() == null || bed.getBedNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Bed number is mandatory");
        }
        if (bed.getStatus() == null || bed.getStatus().trim().isEmpty()) {
            bed.setStatus("AVAILABLE");
        }
        String wardName = bed.getWard() != null ? bed.getWard() : "General";
        Optional<Bed> duplicate = bedRepository.findByBedNumberAndWard(bed.getBedNumber().trim(), wardName);
        if (duplicate.isPresent()) {
            throw new DuplicateResourceException("Bed number " + bed.getBedNumber() + " already exists in ward " + wardName);
        }

        if (bed.getDailyRate() == null && bed.getPricePerDay() != null) {
            bed.setDailyRate(BigDecimal.valueOf(bed.getPricePerDay()));
        } else if (bed.getPricePerDay() == null && bed.getDailyRate() != null) {
            bed.setPricePerDay(bed.getDailyRate().doubleValue());
        }

        Instant now = Instant.now();
        bed.setCreatedAt(now);
        bed.setUpdatedAt(now);
        bed.setLastStateChangedAt(now);
        return bedRepository.save(bed);
    }

    @Override
    public List<Bed> getAllBeds() {
        return bedRepository.findAll();
    }

    @Override
    public List<BedSummaryDTO> getAvailableBeds() {
        List<Bed> available = bedRepository.findByStatus("AVAILABLE");
        return available.stream()
                .filter(Bed::isActive)
                .map(this::toSummaryDTO)
                .toList();
    }

    @Override
    public Bed getBedById(String id) {
        return bedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + id));
    }

    @Override
    public Bed updateBed(String id, Bed bed) {
        Bed existing = getBedById(id);

        // Guard: Prevent manual status override away from OCCUPIED if an active booking exists
        if ("OCCUPIED".equalsIgnoreCase(existing.getStatus()) && !"OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            Optional<BedBooking> activeBooking = bedBookingRepository.findFirstByBedIdAndStatusIn(
                    id, List.of("ACTIVE", "ADMITTED", "TRANSFER_PENDING", "DISCHARGE_READY")
            );
            boolean hasActive = activeBooking.isPresent() || bedBookingRepository.findAll().stream()
                    .anyMatch(b -> id.equals(b.getBedId()) && ("ACTIVE".equalsIgnoreCase(b.getStatus()) || "ADMITTED".equalsIgnoreCase(b.getStatus())));
            if (hasActive) {
                String admNum = activeBooking.map(BedBooking::getAdmissionNumber).orElse("ACTIVE");
                throw new InvalidBookingTransitionException(
                        "Cannot modify bed status away from OCCUPIED while an active admission ("
                                + admNum + ") exists. Please discharge the patient first.");
            }
        }

        // Guard: Prevent setting status to OCCUPIED via direct updateBed API (must use admitPatient)
        if (!"OCCUPIED".equalsIgnoreCase(existing.getStatus()) && "OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot manually set bed status to OCCUPIED. Use the admission workflow.");
        }

        // Guard: Prevent resetting CLEANING bed directly to AVAILABLE without turnover protocol
        if ("CLEANING".equalsIgnoreCase(existing.getStatus()) && "AVAILABLE".equalsIgnoreCase(bed.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot bypass cleaning protocol. Use markBedReady endpoint.");
        }

        existing.setBedNumber(bed.getBedNumber());
        existing.setType(bed.getType());
        existing.setStatus(bed.getStatus());
        existing.setPricePerDay(bed.getPricePerDay());
        if (bed.getDailyRate() != null) {
            existing.setDailyRate(bed.getDailyRate());
        } else if (bed.getPricePerDay() != null) {
            existing.setDailyRate(BigDecimal.valueOf(bed.getPricePerDay()));
        }
        existing.setWard(bed.getWard());
        existing.setWardId(bed.getWardId());
        existing.setLocationCode(bed.getLocationCode());
        existing.setActive(bed.isActive());
        existing.setUpdatedAt(Instant.now());
        return bedRepository.save(existing);
    }

    @Override
    public void deleteBed(String id) {
        Bed existing = getBedById(id);
        if ("OCCUPIED".equalsIgnoreCase(existing.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot delete bed while occupied");
        }
        Optional<BedBooking> active = bedBookingRepository.findFirstByBedIdAndStatusIn(
                id, List.of("ACTIVE", "ADMITTED", "TRANSFER_PENDING", "DISCHARGE_READY")
        );
        if (active.isPresent()) {
            throw new InvalidBookingTransitionException("Cannot delete bed with an active admission");
        }
        bedRepository.deleteById(id);
    }

    @Override
    public BedBooking bookBed(BedBooking booking) {
        if (admissionService != null) {
            AdmissionRequestDTO request = AdmissionRequestDTO.builder()
                    .patientId(booking.getPatientId())
                    .doctorId(booking.getDoctorId())
                    .bedId(booking.getBedId())
                    .notes(booking.getNotes())
                    .admissionSource(booking.getAdmissionSource())
                    .reasonCode(booking.getReasonCode())
                    .idempotencyKey(booking.getIdempotencyKey())
                    .build();

            AdmissionResponseDTO response = admissionService.admitPatient(request);
            return bedBookingRepository.findById(response.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking created but not found: " + response.getId()));
        }

        // Direct atomic claim fallback for tests where AdmissionService is not wired
        if (booking.getBedId() == null || booking.getBedId().isBlank()) {
            throw new ResourceNotFoundException("Bed ID must not be empty");
        }

        Query claimQuery = Query.query(Criteria.where("_id").is(booking.getBedId()).and("status").is("AVAILABLE"));
        Update claimUpdate = new Update().set("status", "OCCUPIED").set("lastStateChangedAt", Instant.now());
        Bed claimedBed = mongoTemplate.findAndModify(claimQuery, claimUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (claimedBed == null) {
            Bed bed = bedRepository.findById(booking.getBedId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + booking.getBedId()));
            log.warn("Bed allocation conflict: Bed {} is in status {} (not AVAILABLE)", bed.getId(), bed.getStatus());
            throw new BedAlreadyOccupiedException("This bed has already been allocated. Please select another bed.");
        }

        booking.setStatus("ACTIVE");
        if (booking.getAdmissionDate() == null || booking.getAdmissionDate().isBlank()) {
            booking.setAdmissionDate(LocalDate.now().toString());
        }
        if (booking.getAdmittedAt() == null) {
            booking.setAdmittedAt(Instant.now());
        }
        if (booking.getAdmissionNumber() == null) {
            booking.setAdmissionNumber("ADM-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 9000 + 1000));
        }

        BedBooking savedBooking;
        try {
            savedBooking = bedBookingRepository.save(booking);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(claimedBed.getId())),
                    new Update().set("currentBookingId", savedBooking.getId()).set("currentPatientId", savedBooking.getPatientId()),
                    Bed.class
            );
        } catch (Exception ex) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(claimedBed.getId()).and("status").is("OCCUPIED")),
                    new Update().set("status", "AVAILABLE").unset("currentBookingId").unset("currentPatientId"),
                    Bed.class
            );
            throw ex;
        }

        return savedBooking;
    }

    @Override
    public List<BedBooking> getPatientBookings(String patientId) {
        return bedBookingRepository.findByPatientId(patientId);
    }

    @Override
    public BedBooking dischargePatient(String bookingId) {
        if (admissionService != null) {
            admissionService.dischargePatient(bookingId, new DischargeRequestDTO());
            return bedBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        }

        if (bookingId == null || bookingId.isBlank()) {
            throw new ResourceNotFoundException("Booking ID must not be empty");
        }

        Query dischargeQuery = Query.query(Criteria.where("_id").is(bookingId).and("status").in("ACTIVE", "ADMITTED", "DISCHARGE_READY"));
        Update dischargeUpdate = new Update()
                .set("status", "DISCHARGED")
                .set("dischargeDate", LocalDate.now().toString())
                .set("dischargedAt", Instant.now());

        BedBooking dischargedBooking = mongoTemplate.findAndModify(dischargeQuery, dischargeUpdate,
                FindAndModifyOptions.options().returnNew(true), BedBooking.class);

        if (dischargedBooking == null) {
            BedBooking existing = bedBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
            log.warn("Discharge conflict: Booking {} is already in status {}", existing.getId(), existing.getStatus());
            throw new InvalidBookingTransitionException("Bed booking is not active or has already been discharged");
        }

        Query bedReleaseQuery = Query.query(Criteria.where("_id").is(dischargedBooking.getBedId()).and("currentBookingId").is(dischargedBooking.getId()));
        Update bedReleaseUpdate = new Update()
                .set("status", "AVAILABLE")
                .unset("currentBookingId")
                .unset("currentPatientId")
                .set("lastStateChangedAt", Instant.now());

        mongoTemplate.updateFirst(bedReleaseQuery, bedReleaseUpdate, Bed.class);

        return dischargedBooking;
    }

    @Override
    public List<BedBooking> getAllBookings() {
        return bedBookingRepository.findAll();
    }

    private BedSummaryDTO toSummaryDTO(Bed bed) {
        return BedSummaryDTO.builder()
                .id(bed.getId())
                .bedNumber(bed.getBedNumber())
                .type(bed.getType())
                .status(bed.getStatus())
                .ward(bed.getWard())
                .wardId(bed.getWardId())
                .locationCode(bed.getLocationCode())
                .dailyRate(bed.getAuthoritativeRate())
                .pricePerDay(bed.getPricePerDay())
                .active(bed.isActive())
                .build();
    }
}
