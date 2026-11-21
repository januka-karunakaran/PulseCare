package com.hospital.backend.service.impl;

import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.repository.BedBookingRepository;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.service.BedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Enterprise Bed Service implementation with database-enforced atomic concurrency controls.
 * Guarantees zero bed over-allocation and safe single-discharge invariants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BedServiceImpl implements BedService {

    private final BedRepository bedRepository;
    private final BedBookingRepository bedBookingRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public Bed addBed(Bed bed) {
        if (bed.getStatus() == null) {
            bed.setStatus("AVAILABLE");
        }
        return bedRepository.save(bed);
    }

    @Override
    public List<Bed> getAllBeds() {
        return bedRepository.findAll();
    }

    @Override
    public Bed getBedById(String id) {
        return bedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + id));
    }

    @Override
    public Bed updateBed(String id, Bed bed) {
        Bed existing = getBedById(id);

        // Guard: Prevent manual status override to AVAILABLE if an active booking exists
        if ("OCCUPIED".equalsIgnoreCase(existing.getStatus()) && !"OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            boolean hasActiveBooking = bedBookingRepository.findAll().stream()
                    .anyMatch(b -> id.equals(b.getBedId()) && "ACTIVE".equalsIgnoreCase(b.getStatus()));
            if (hasActiveBooking) {
                throw new InvalidBookingTransitionException(
                        "Cannot modify bed status away from OCCUPIED while an active booking exists. Please discharge the patient first.");
            }
        }

        existing.setBedNumber(bed.getBedNumber());
        existing.setType(bed.getType());
        existing.setStatus(bed.getStatus());
        existing.setPricePerDay(bed.getPricePerDay());
        existing.setWard(bed.getWard());
        return bedRepository.save(existing);
    }

    @Override
    public void deleteBed(String id) {
        Bed existing = getBedById(id);
        if ("OCCUPIED".equalsIgnoreCase(existing.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot delete bed while occupied");
        }
        boolean hasActiveBooking = bedBookingRepository.findAll().stream()
                .anyMatch(b -> id.equals(b.getBedId()) && "ACTIVE".equalsIgnoreCase(b.getStatus()));
        if (hasActiveBooking) {
            throw new InvalidBookingTransitionException("Cannot delete bed with an active booking");
        }
        bedRepository.deleteById(id);
    }

    @Override
    public BedBooking bookBed(BedBooking booking) {
        if (booking.getBedId() == null || booking.getBedId().isBlank()) {
            throw new ResourceNotFoundException("Bed ID must not be empty");
        }

        // 1. Atomic conditional claim on Bed document: status must be AVAILABLE
        Query claimQuery = Query.query(Criteria.where("_id").is(booking.getBedId()).and("status").is("AVAILABLE"));
        Update claimUpdate = new Update().set("status", "OCCUPIED");
        Bed claimedBed = mongoTemplate.findAndModify(claimQuery, claimUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (claimedBed == null) {
            // Check if bed exists to give the right error
            Bed bed = bedRepository.findById(booking.getBedId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + booking.getBedId()));
            log.warn("Bed allocation conflict: Bed {} is in status {} (not AVAILABLE)", bed.getId(), bed.getStatus());
            throw new BedAlreadyOccupiedException("This bed has already been allocated. Please select another bed.");
        }

        // 2. Prepare BedBooking
        booking.setStatus("ACTIVE");
        if (booking.getAdmissionDate() == null || booking.getAdmissionDate().isBlank()) {
            booking.setAdmissionDate(LocalDate.now().toString());
        }

        BedBooking savedBooking;
        try {
            savedBooking = bedBookingRepository.save(booking);

            // Link bed to active booking
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(claimedBed.getId())),
                    new Update().set("currentBookingId", savedBooking.getId()),
                    Bed.class
            );
        } catch (Exception ex) {
            log.error("Failed to save bed booking after bed claim. Executing compensating rollback for bed {}", claimedBed.getId(), ex);
            // Compensating rollback
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(claimedBed.getId()).and("status").is("OCCUPIED")),
                    new Update().set("status", "AVAILABLE").unset("currentBookingId"),
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
        if (bookingId == null || bookingId.isBlank()) {
            throw new ResourceNotFoundException("Booking ID must not be empty");
        }

        // 1. Atomic conditional update on BedBooking: must be in ACTIVE status
        Query dischargeQuery = Query.query(Criteria.where("_id").is(bookingId).and("status").is("ACTIVE"));
        Update dischargeUpdate = new Update()
                .set("status", "DISCHARGED")
                .set("dischargeDate", LocalDate.now().toString());

        BedBooking dischargedBooking = mongoTemplate.findAndModify(dischargeQuery, dischargeUpdate,
                FindAndModifyOptions.options().returnNew(true), BedBooking.class);

        if (dischargedBooking == null) {
            BedBooking existing = bedBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
            log.warn("Discharge conflict: Booking {} is already in status {}", existing.getId(), existing.getStatus());
            throw new InvalidBookingTransitionException("Bed booking is not active or has already been discharged");
        }

        // 2. Atomic release of Bed document: only release if status is OCCUPIED
        Query bedReleaseQuery = Query.query(Criteria.where("_id").is(dischargedBooking.getBedId()).and("status").is("OCCUPIED"));
        Update bedReleaseUpdate = new Update()
                .set("status", "AVAILABLE")
                .unset("currentBookingId");

        mongoTemplate.updateFirst(bedReleaseQuery, bedReleaseUpdate, Bed.class);

        return dischargedBooking;
    }

    @Override
    public List<BedBooking> getAllBookings() {
        return bedBookingRepository.findAll();
    }
}
