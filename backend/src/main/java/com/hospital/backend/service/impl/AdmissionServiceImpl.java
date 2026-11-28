package com.hospital.backend.service.impl;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.dto.bed.AdmissionResponseDTO;
import com.hospital.backend.dto.bed.DischargeRequestDTO;
import com.hospital.backend.dto.bed.TransferRequestDTO;
import com.hospital.backend.enums.ChargeSourceType;
import com.hospital.backend.enums.OutboxChannel;
import com.hospital.backend.enums.OutboxStatus;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.AdmissionService;
import com.hospital.backend.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionServiceImpl implements AdmissionService {

    private final BedBookingRepository bedBookingRepository;
    private final BedRepository bedRepository;
    private final HospitalWardRepository wardRepository;
    private final BedOccupancyEventRepository eventRepository;
    private final BedTransferRecordRepository transferRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final ClinicalEncounterRepository clinicalEncounterRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthorizationService authz;
    private final BillingService billingService;

    private static final List<String> ACTIVE_STATUSES = List.of(
            "ACTIVE", "ADMITTED", "TRANSFER_PENDING", "DISCHARGE_READY"
    );

    @Override
    @Transactional
    public AdmissionResponseDTO admitPatient(AdmissionRequestDTO request) {
        if (request.getPatientId() == null || request.getPatientId().isBlank()) {
            throw new IllegalArgumentException("Patient ID is mandatory");
        }
        if (request.getBedId() == null || request.getBedId().isBlank()) {
            throw new IllegalArgumentException("Bed ID is mandatory");
        }

        // 1. Resolve canonical patient identity
        Patient patient = patientRepository.findById(request.getPatientId())
                .or(() -> patientRepository.findByUserId(request.getPatientId()))
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.getPatientId()));

        // 2. Authorization check
        if (!authz.canAccessPatient(patient.getId())) {
            throw new AccessDeniedException("Not authorized to admit patient: " + patient.getId());
        }

        User actor = authz.getCurrentUser();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.DOCTOR && actor.getRole() != Role.BED_MANAGER) {
            throw new AccessDeniedException("Only ADMIN, DOCTOR, or BED_MANAGER can admit patients");
        }

        // 3. Resolve attending doctor
        String doctorId = request.getDoctorId();
        Doctor doctor = null;
        if (doctorId != null && !doctorId.isBlank()) {
            doctor = doctorRepository.findById(doctorId)
                    .or(() -> doctorRepository.findByUserId(doctorId))
                    .orElseThrow(() -> new ResourceNotFoundException("Attending doctor not found: " + doctorId));
        } else if (actor.getRole() == Role.DOCTOR) {
            doctor = doctorRepository.findByUserId(actor.getId())
                    .or(() -> doctorRepository.findById(actor.getId()))
                    .orElse(null);
        }

        // 3b. Validate associated Clinical Encounter if provided
        if (request.getClinicalEncounterId() != null && !request.getClinicalEncounterId().isBlank()) {
            ClinicalEncounter encounter = clinicalEncounterRepository.findById(request.getClinicalEncounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Clinical encounter not found: " + request.getClinicalEncounterId()));
            if (!patient.getId().equals(encounter.getPatientId())) {
                throw new IllegalArgumentException("Clinical encounter " + encounter.getId() + " does not belong to patient " + patient.getId());
            }
        }

        // 4. Idempotency check with payload verification
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<BedBooking> existing = bedBookingRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                BedBooking prev = existing.get();
                if (!patient.getId().equals(prev.getPatientId()) || !request.getBedId().equals(prev.getBedId())) {
                    throw new DuplicateResourceException("Idempotency key '" + request.getIdempotencyKey()
                            + "' already used for a different admission payload");
                }
                log.info("Idempotent admission request returned existing admission: {}", prev.getId());
                return toResponseDTO(prev, patient, doctor);
            }
        }

        // 5. Prevent duplicate active admissions for the same patient
        Optional<BedBooking> activeAdmission = bedBookingRepository.findFirstByPatientIdAndStatusIn(patient.getId(), ACTIVE_STATUSES);
        if (activeAdmission.isPresent()) {
            throw new InvalidBookingTransitionException("Patient already has an active hospital admission ("
                    + activeAdmission.get().getAdmissionNumber() + ") in bed " + activeAdmission.get().getBedId());
        }

        Instant now = Instant.now();

        // 6. Atomic Bed Claim (Condition: status must be AVAILABLE and bed must be active)
        Query claimQuery = Query.query(Criteria.where("_id").is(request.getBedId())
                .and("status").is("AVAILABLE")
                .and("active").ne(false));
        Update claimUpdate = new Update()
                .set("status", "OCCUPIED")
                .set("lastStateChangedAt", now);

        Bed claimedBed = mongoTemplate.findAndModify(claimQuery, claimUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (claimedBed == null) {
            Bed bed = bedRepository.findById(request.getBedId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + request.getBedId()));
            if (!bed.isActive()) {
                throw new InvalidBookingTransitionException("Bed is deactivated and cannot accept admissions");
            }
            if ("MAINTENANCE".equalsIgnoreCase(bed.getStatus())) {
                throw new InvalidBookingTransitionException("Bed is currently undergoing maintenance");
            }
            if ("CLEANING".equalsIgnoreCase(bed.getStatus())) {
                throw new InvalidBookingTransitionException("Bed is currently undergoing turnover/cleaning");
            }
            log.warn("Bed allocation conflict: Bed {} is in status {} (not AVAILABLE)", bed.getId(), bed.getStatus());
            throw new BedAlreadyOccupiedException("This bed has already been allocated. Please select another bed.");
        }

        // 7. Generate Unique Admission Number
        String admissionNumber = "ADM-" + now.toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        // 8. Construct and Persist BedBooking / Admission
        BedBooking booking = BedBooking.builder()
                .admissionNumber(admissionNumber)
                .bedId(claimedBed.getId())
                .currentBedId(claimedBed.getId())
                .currentWardId(claimedBed.getWardId() != null ? claimedBed.getWardId() : claimedBed.getWard())
                .patientId(patient.getId())
                .doctorId(doctor != null ? doctor.getId() : null)
                .admissionDate(LocalDate.now().toString())
                .admittedAt(now)
                .status("ADMITTED")
                .admissionSource(request.getAdmissionSource() != null ? request.getAdmissionSource() : "ELECTIVE")
                .reasonCode(request.getReasonCode())
                .notes(request.getNotes())
                .clinicalEncounterId(request.getClinicalEncounterId())
                .createdByUserId(actor.getId())
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        BedBooking savedBooking = bedBookingRepository.save(booking);

        // 9. Link Bed document to currentBookingId and currentPatientId
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(claimedBed.getId())),
                new Update()
                        .set("currentBookingId", savedBooking.getId())
                        .set("currentPatientId", patient.getId()),
                Bed.class
        );

        // 10. Append Immutable Occupancy Event
        BedOccupancyEvent event = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(savedBooking.getId())
                .patientId(patient.getId())
                .bedId(claimedBed.getId())
                .wardId(claimedBed.getWardId() != null ? claimedBed.getWardId() : claimedBed.getWard())
                .eventType("BED_OCCUPIED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode(request.getReasonCode())
                .sequenceNumber(1L)
                .metadata(Map.of("action", "INITIAL_ADMISSION", "admissionNumber", admissionNumber))
                .build();
        eventRepository.save(event);

        // 11. Enqueue Durable Notification
        enqueueNotification(
                "BED_ADMISSION_CONFIRMED",
                savedBooking.getId(),
                patient.getUserId(),
                "PATIENT",
                "Your admission to bed " + claimedBed.getBedNumber() + " has been confirmed."
        );

        return toResponseDTO(savedBooking, patient, doctor);
    }

    @Override
    @Transactional
    public BedTransferRecord transferBed(String admissionId, TransferRequestDTO request) {
        if (admissionId == null || admissionId.isBlank()) {
            throw new IllegalArgumentException("Admission ID is mandatory");
        }
        if (request.getDestinationBedId() == null || request.getDestinationBedId().isBlank()) {
            throw new IllegalArgumentException("Destination bed ID is mandatory");
        }

        // 1. Idempotency check with payload verification
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<BedTransferRecord> existing = transferRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                BedTransferRecord prev = existing.get();
                if (!admissionId.equals(prev.getAdmissionId()) || !request.getDestinationBedId().equals(prev.getDestinationBedId())) {
                    throw new DuplicateResourceException("Idempotency key '" + request.getIdempotencyKey()
                            + "' already used for a different transfer payload");
                }
                log.info("Idempotent transfer request returned existing record: {}", prev.getId());
                return prev;
            }
        }

        // 2. Validate admission
        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if (!admission.isActive()) {
            throw new InvalidBookingTransitionException("Cannot transfer patient: Admission " + admissionId
                    + " is in status " + admission.getStatus());
        }

        String sourceBedId = admission.getCurrentBedId() != null ? admission.getCurrentBedId() : admission.getBedId();
        if (request.getDestinationBedId().equals(sourceBedId)) {
            throw new InvalidBookingTransitionException("Destination bed cannot be the same as current bed");
        }

        User actor = authz.getCurrentUser();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.DOCTOR && actor.getRole() != Role.BED_MANAGER) {
            throw new AccessDeniedException("Not authorized to transfer inpatient beds");
        }

        Instant now = Instant.now();

        // 3. Atomic Destination Bed Claim
        Query destClaimQuery = Query.query(Criteria.where("_id").is(request.getDestinationBedId())
                .and("status").is("AVAILABLE")
                .and("active").ne(false));
        Update destClaimUpdate = new Update()
                .set("status", "OCCUPIED")
                .set("currentBookingId", admission.getId())
                .set("currentPatientId", admission.getPatientId())
                .set("lastStateChangedAt", now);

        Bed destBed = mongoTemplate.findAndModify(destClaimQuery, destClaimUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (destBed == null) {
            throw new BedAlreadyOccupiedException("Destination bed " + request.getDestinationBedId() + " is already occupied or not available");
        }

        // 4. Atomic Source Bed Release to CLEANING status (Condition: currentBookingId matches admission)
        Query srcReleaseQuery = Query.query(Criteria.where("_id").is(sourceBedId)
                .and("currentBookingId").is(admission.getId()));
        Update srcReleaseUpdate = new Update()
                .set("status", "CLEANING")
                .unset("currentBookingId")
                .unset("currentPatientId")
                .set("lastStateChangedAt", now);

        Bed srcBed = mongoTemplate.findAndModify(srcReleaseQuery, srcReleaseUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (srcBed == null) {
            log.error("Transfer failure: Source bed {} does not belong to active admission {}", sourceBedId, admission.getId());
            throw new InvalidBookingTransitionException("Source bed is no longer associated with this admission");
        }

        // 5. Update Admission Record
        String destWardId = destBed.getWardId() != null ? destBed.getWardId() : destBed.getWard();
        admission.setBedId(destBed.getId());
        admission.setCurrentBedId(destBed.getId());
        admission.setCurrentWardId(destWardId);
        admission.setUpdatedAt(now);
        bedBookingRepository.save(admission);

        // 6. Create BedTransferRecord
        String transferNumber = "TRF-" + now.toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        BedTransferRecord transferRecord = BedTransferRecord.builder()
                .transferNumber(transferNumber)
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .sourceBedId(sourceBedId)
                .sourceWardId(srcBed.getWardId() != null ? srcBed.getWardId() : srcBed.getWard())
                .destinationBedId(destBed.getId())
                .destinationWardId(destWardId)
                .transferredAt(now)
                .actorUserId(actor.getId())
                .reasonCode(request.getReasonCode())
                .notes(request.getNotes())
                .createdAt(now)
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .build();
        BedTransferRecord savedTransfer = transferRepository.save(transferRecord);

        // 7. Append Immutable Occupancy Events (Vacate Source, Occupy Destination, Complete Transfer)
        long currentSeq = eventRepository.countByAdmissionId(admission.getId());

        BedOccupancyEvent vacateEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(sourceBedId)
                .wardId(srcBed.getWardId() != null ? srcBed.getWardId() : srcBed.getWard())
                .eventType("BED_VACATED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode("TRANSFER_OUT")
                .destinationBedId(destBed.getId())
                .sequenceNumber(currentSeq + 1)
                .metadata(Map.of("transferNumber", transferNumber))
                .build();
        eventRepository.save(vacateEvent);

        BedOccupancyEvent occupyEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(destBed.getId())
                .wardId(destWardId)
                .eventType("BED_OCCUPIED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode("TRANSFER_IN")
                .sourceBedId(sourceBedId)
                .sequenceNumber(currentSeq + 2)
                .metadata(Map.of("transferNumber", transferNumber))
                .build();
        eventRepository.save(occupyEvent);

        BedOccupancyEvent completeEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(destBed.getId())
                .wardId(destWardId)
                .eventType("TRANSFER_COMPLETED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode(request.getReasonCode())
                .sourceBedId(sourceBedId)
                .destinationBedId(destBed.getId())
                .sequenceNumber(currentSeq + 3)
                .metadata(Map.of("transferNumber", transferNumber))
                .build();
        eventRepository.save(completeEvent);

        // 8. Enqueue Durable Notification
        enqueueNotification(
                "BED_TRANSFER_COMPLETED",
                admission.getId(),
                admission.getPatientId(),
                "PATIENT",
                "Patient transferred from Bed " + srcBed.getBedNumber() + " to Bed " + destBed.getBedNumber()
        );

        return savedTransfer;
    }

    @Override
    public AdmissionResponseDTO prepareDischarge(String admissionId, DischargeRequestDTO request) {
        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if (!admission.isActive()) {
            throw new InvalidBookingTransitionException("Admission is not active: " + admission.getStatus());
        }

        User actor = authz.getCurrentUser();
        // Clinical discharge readiness must strictly be certified by a verified DOCTOR with a clinical relationship
        if (actor.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only an authorized treating physician (DOCTOR) can certify clinical discharge readiness");
        }

        if (!authz.hasDoctorPatientRelationship(actor.getId(), admission.getPatientId())) {
            throw new AccessDeniedException("Doctor does not have an established clinical relationship with this patient");
        }

        Instant now = Instant.now();
        admission.setStatus("DISCHARGE_READY");
        admission.setClinicalClearanceByDoctorId(actor.getId());
        admission.setDischargeReadyAt(now);
        admission.setUpdatedAt(now);
        BedBooking saved = bedBookingRepository.save(admission);

        return toResponseDTO(saved, null, null);
    }

    @Override
    @Transactional
    public AdmissionResponseDTO dischargePatient(String admissionId, DischargeRequestDTO request) {
        if (admissionId == null || admissionId.isBlank()) {
            throw new IllegalArgumentException("Admission ID is mandatory");
        }

        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if ("DISCHARGED".equalsIgnoreCase(admission.getStatus())) {
            log.info("Admission {} is already discharged. Returning idempotent response.", admissionId);
            return toResponseDTO(admission, null, null);
        }

        if (!"DISCHARGE_READY".equalsIgnoreCase(admission.getStatus())) {
            throw new InvalidBookingTransitionException(
                    "Admission must be clinically cleared (DISCHARGE_READY) before discharge. Current status: " + admission.getStatus());
        }

        User actor = authz.getCurrentUser();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.DOCTOR && actor.getRole() != Role.BED_MANAGER) {
            throw new AccessDeniedException("Not authorized to discharge inpatient admissions");
        }

        Instant now = Instant.now();
        String currentBedId = admission.getCurrentBedId() != null ? admission.getCurrentBedId() : admission.getBedId();

        // 1. Atomic release of Bed document (Condition: currentBookingId strictly equals this admission)
        Query releaseQuery = Query.query(Criteria.where("_id").is(currentBedId)
                .and("currentBookingId").is(admission.getId()));
        Update releaseUpdate = new Update()
                .set("status", "CLEANING") // Turnover state after patient departure
                .unset("currentBookingId")
                .unset("currentPatientId")
                .set("lastStateChangedAt", now);

        Bed releasedBed = mongoTemplate.findAndModify(releaseQuery, releaseUpdate,
                FindAndModifyOptions.options().returnNew(true), Bed.class);

        if (releasedBed == null) {
            // Bed currentBookingId does not match this admission
            log.error("Discharge aborted: Bed {} currentBookingId mismatch for admission {}", currentBedId, admission.getId());
            throw new InvalidBookingTransitionException("Bed currentBookingId does not match the admission being discharged");
        }

        // 2. Update Admission to DISCHARGED
        admission.setStatus("DISCHARGED");
        admission.setDischargedAt(now);
        admission.setDischargeDate(LocalDate.now().toString());
        admission.setDischargedByUserId(actor.getId());
        admission.setUpdatedAt(now);
        if (request != null && request.getNotes() != null) {
            admission.setNotes(admission.getNotes() != null ? admission.getNotes() + "\n" + request.getNotes() : request.getNotes());
        }
        BedBooking dischargedAdmission = bedBookingRepository.save(admission);

        // 3. Append Immutable Occupancy Events
        long currentSeq = eventRepository.countByAdmissionId(admission.getId());

        BedOccupancyEvent vacateEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(currentBedId)
                .wardId(admission.getCurrentWardId())
                .eventType("BED_VACATED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode("DISCHARGE")
                .sequenceNumber(currentSeq + 1)
                .build();
        eventRepository.save(vacateEvent);

        BedOccupancyEvent dischargeEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(currentBedId)
                .wardId(admission.getCurrentWardId())
                .eventType("DISCHARGE_COMPLETED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .reasonCode("NORMAL_DISCHARGE")
                .sequenceNumber(currentSeq + 2)
                .build();
        eventRepository.save(dischargeEvent);

        BedOccupancyEvent turnoverEvent = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .bedId(currentBedId)
                .wardId(admission.getCurrentWardId())
                .eventType("BED_TURNOVER_STARTED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(actor.getId())
                .sequenceNumber(currentSeq + 3)
                .build();
        eventRepository.save(turnoverEvent);

        // 4. Safe Billing Integration (Non-blocking: financial errors must never abort clinical discharge)
        try {
            recordBedStayBillingCharge(dischargedAdmission, releasedBed);
        } catch (Exception ex) {
            log.error("Deferred billing registration failed for admission {}. Manual billing reconciliation required.", admission.getId(), ex);
        }

        // 5. Enqueue Notification
        enqueueNotification(
                "BED_DISCHARGE_COMPLETED",
                admission.getId(),
                admission.getPatientId(),
                "PATIENT",
                "Hospital discharge completed. We wish you a speedy recovery."
        );

        return toResponseDTO(dischargedAdmission, null, null);
    }

    private void recordBedStayBillingCharge(BedBooking admission, Bed bed) {
        if (admission == null || admission.getAdmittedAt() == null || admission.getDischargedAt() == null) {
            return;
        }

        Duration duration = Duration.between(admission.getAdmittedAt(), admission.getDischargedAt());
        long totalMinutes = Math.max(0, duration.toMinutes());
        // Billable days: Minimum 1 day. Any partial day after 24h is calculated in exact 24h blocks or ceiling
        long billableDays = Math.max(1, (totalMinutes + 1439) / 1440);

        BigDecimal authoritativeRate = (bed != null) ? bed.getAuthoritativeRate() : BigDecimal.ZERO;
        String bedNumber = bed != null ? bed.getBedNumber() : admission.getCurrentBedId();

        if (authoritativeRate == null || authoritativeRate.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Bed {} has no authoritative rate configured. Registering pending rate charge for admission {}",
                    bedNumber, admission.getId());
        }

        String description = "Inpatient Bed Stay: Bed " + bedNumber + " (" + billableDays + " days)";

        billingService.registerClinicalCharge(
                ChargeSourceType.BED_STAY,
                admission.getId(),
                "STAY_" + admission.getId() + "_DAYS_" + billableDays,
                admission.getPatientId(),
                "BED_STAY_STANDARD",
                BigDecimal.valueOf(billableDays),
                description
        );
    }

    @Override
    @Transactional
    public Bed markBedReady(String bedId) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        if (!"CLEANING".equalsIgnoreCase(bed.getStatus())) {
            throw new InvalidBookingTransitionException(
                    "Bed must be in CLEANING status before being marked AVAILABLE. Current status: " + bed.getStatus());
        }

        Instant now = Instant.now();
        bed.setStatus("AVAILABLE");
        bed.setCurrentBookingId(null);
        bed.setCurrentPatientId(null);
        bed.setLastStateChangedAt(now);
        bed.setUpdatedAt(now);
        Bed saved = bedRepository.save(bed);

        BedOccupancyEvent event = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bedId(bed.getId())
                .wardId(bed.getWardId() != null ? bed.getWardId() : bed.getWard())
                .eventType("BED_AVAILABLE")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(authz.getCurrentUser().getId())
                .reasonCode("TURNOVER_COMPLETED")
                .sequenceNumber(0L)
                .build();
        eventRepository.save(event);

        return saved;
    }

    @Override
    @Transactional
    public Bed markBedCleaning(String bedId) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot mark an OCCUPIED bed for cleaning. Discharge the patient first.");
        }

        Instant now = Instant.now();
        bed.setStatus("CLEANING");
        bed.setLastStateChangedAt(now);
        bed.setUpdatedAt(now);
        Bed saved = bedRepository.save(bed);

        BedOccupancyEvent event = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bedId(bed.getId())
                .wardId(bed.getWardId() != null ? bed.getWardId() : bed.getWard())
                .eventType("BED_TURNOVER_STARTED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(authz.getCurrentUser().getId())
                .reasonCode("MANUAL_TURNOVER_INITIATED")
                .sequenceNumber(0L)
                .build();
        eventRepository.save(event);

        return saved;
    }

    @Override
    @Transactional
    public Bed markBedMaintenance(String bedId) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        if ("OCCUPIED".equalsIgnoreCase(bed.getStatus())) {
            throw new InvalidBookingTransitionException("Cannot place an OCCUPIED bed under maintenance. Discharge or transfer patient first.");
        }

        Instant now = Instant.now();
        bed.setStatus("MAINTENANCE");
        bed.setLastStateChangedAt(now);
        bed.setUpdatedAt(now);
        Bed saved = bedRepository.save(bed);

        BedOccupancyEvent event = BedOccupancyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bedId(bed.getId())
                .wardId(bed.getWardId() != null ? bed.getWardId() : bed.getWard())
                .eventType("BED_MAINTENANCE_STARTED")
                .occurredAt(now)
                .recordedAt(now)
                .actorUserId(authz.getCurrentUser().getId())
                .reasonCode("ROUTINE_MAINTENANCE")
                .sequenceNumber(0L)
                .build();
        eventRepository.save(event);

        return saved;
    }

    @Override
    public AdmissionResponseDTO getAdmissionById(String admissionId) {
        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if (!authz.canAccessPatient(admission.getPatientId())) {
            throw new AccessDeniedException("Not authorized to view admission: " + admissionId);
        }

        return toResponseDTO(admission, null, null);
    }

    @Override
    public List<AdmissionResponseDTO> getActiveAdmissions() {
        List<BedBooking> active = bedBookingRepository.findByStatusIn(ACTIVE_STATUSES);
        return active.stream().map(b -> toResponseDTO(b, null, null)).toList();
    }

    @Override
    public List<AdmissionResponseDTO> getPatientAdmissions(String patientId) {
        if (!authz.canAccessPatient(patientId)) {
            throw new AccessDeniedException("Not authorized to view admissions for patient: " + patientId);
        }
        List<BedBooking> list = bedBookingRepository.findByPatientIdOrderByAdmittedAtDesc(patientId);
        if (list.isEmpty()) {
            list = bedBookingRepository.findByPatientId(patientId);
        }
        return list.stream().map(b -> toResponseDTO(b, null, null)).toList();
    }

    @Override
    public List<AdmissionResponseDTO> getDoctorAdmissions(String doctorId) {
        User currentUser = authz.getCurrentUser();
        String effectiveDoctorId = doctorId;

        // If caller is DOCTOR, resolve doctor profile from authenticated user to prevent IDOR
        if (currentUser.getRole() == Role.DOCTOR) {
            Doctor doc = doctorRepository.findByUserId(currentUser.getId())
                    .or(() -> doctorRepository.findById(currentUser.getId()))
                    .orElse(null);
            if (doc != null) {
                effectiveDoctorId = doc.getId();
            } else {
                effectiveDoctorId = currentUser.getId();
            }
        }

        List<BedBooking> list = bedBookingRepository.findByDoctorIdAndStatusIn(effectiveDoctorId, ACTIVE_STATUSES);
        return list.stream().map(b -> toResponseDTO(b, null, null)).toList();
    }

    @Override
    public List<BedTransferRecord> getAdmissionTransfers(String admissionId) {
        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if (!authz.canAccessPatient(admission.getPatientId())) {
            throw new AccessDeniedException("Not authorized to view transfer history for admission: " + admissionId);
        }

        return transferRepository.findByAdmissionIdOrderByTransferredAtDesc(admissionId);
    }

    @Override
    public List<BedOccupancyEvent> getAdmissionEvents(String admissionId) {
        BedBooking admission = bedBookingRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + admissionId));

        if (!authz.canAccessPatient(admission.getPatientId())) {
            throw new AccessDeniedException("Not authorized to view occupancy events for admission: " + admissionId);
        }

        return eventRepository.findByAdmissionIdOrderBySequenceNumberAsc(admissionId);
    }

    @Override
    public List<BedOccupancyEvent> getBedOccupancyHistory(String bedId) {
        User user = authz.getCurrentUser();
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.BED_MANAGER && user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Not authorized to view bed occupancy history");
        }
        return eventRepository.findByBedIdOrderByOccurredAtAsc(bedId);
    }

    private void enqueueNotification(String eventType, String aggregateId, String recipientUserId, String recipientRole, String message) {
        try {
            NotificationOutbox outbox = NotificationOutbox.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .recipientUserId(recipientUserId)
                    .recipientRole(recipientRole)
                    .channel(OutboxChannel.IN_APP)
                    .status(OutboxStatus.PENDING)
                    .title("Hospital Inpatient Notification")
                    .message(message)
                    .payloadVersion(1)
                    .maxAttempts(5)
                    .metadata(Map.of("aggregateId", aggregateId, "eventType", eventType))
                    .deduplicationKey(eventType + "_" + aggregateId + "_" + System.currentTimeMillis())
                    .createdAt(Instant.now())
                    .build();
            outboxRepository.save(outbox);
        } catch (Exception ex) {
            log.warn("Failed to enqueue notification outbox for {}: {}", aggregateId, ex.getMessage());
        }
    }

    private AdmissionResponseDTO toResponseDTO(BedBooking booking, Patient patient, Doctor doctor) {
        if (patient == null && booking.getPatientId() != null) {
            patient = patientRepository.findById(booking.getPatientId()).orElse(null);
        }
        if (doctor == null && booking.getDoctorId() != null) {
            doctor = doctorRepository.findById(booking.getDoctorId()).orElse(null);
        }

        String bedId = booking.getCurrentBedId() != null ? booking.getCurrentBedId() : booking.getBedId();
        Bed bed = bedId != null ? bedRepository.findById(bedId).orElse(null) : null;

        Long durationHours = null;
        if (booking.getAdmittedAt() != null) {
            Instant end = booking.getDischargedAt() != null ? booking.getDischargedAt() : Instant.now();
            durationHours = Duration.between(booking.getAdmittedAt(), end).toHours();
        }

        return AdmissionResponseDTO.builder()
                .id(booking.getId())
                .admissionNumber(booking.getAdmissionNumber())
                .patientId(booking.getPatientId())
                .patientName(patient != null ? patient.getFullName() : null)
                .patientEmail(patient != null ? patient.getEmail() : null)
                .doctorId(booking.getDoctorId())
                .doctorName(doctor != null ? doctor.getFullName() : null)
                .bedId(bedId)
                .bedNumber(bed != null ? bed.getBedNumber() : null)
                .ward(bed != null ? bed.getWard() : booking.getCurrentWardId())
                .wardId(booking.getCurrentWardId())
                .wardName(bed != null ? bed.getWard() : null)
                .status(booking.getStatus())
                .admittedAt(booking.getAdmittedAt())
                .dischargedAt(booking.getDischargedAt())
                .admissionDate(booking.getAdmissionDate())
                .dischargeDate(booking.getDischargeDate())
                .admissionSource(booking.getAdmissionSource())
                .reasonCode(booking.getReasonCode())
                .notes(booking.getNotes())
                .clinicalEncounterId(booking.getClinicalEncounterId())
                .clinicalClearanceByDoctorId(booking.getClinicalClearanceByDoctorId())
                .dischargeReadyAt(booking.getDischargeReadyAt())
                .stayDurationHours(durationHours)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
