package com.hospital.backend.service.impl;

import com.hospital.backend.dto.AppointmentRequest;
import com.hospital.backend.dto.AvailableSlotsResponse;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.AppointmentSlotConflictException;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.event.AppointmentEventPublisher;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.AppointmentSlotReservationRepository;
import com.hospital.backend.repository.DoctorRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.AppointmentService;
import com.hospital.backend.service.AvailabilityService;
import com.hospital.backend.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise Appointment Service implementation with database-enforced atomic concurrency controls.
 * Eliminates double-booking of doctor slots and duplicate daily queue tokens across distributed instances.
 */
@Service
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotReservationRepository slotReservationRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;
    private final AvailabilityService availabilityService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AppointmentEventPublisher appointmentEventPublisher;

    @Autowired
    public AppointmentServiceImpl(
            AppointmentRepository appointmentRepository,
            AppointmentSlotReservationRepository slotReservationRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            NotificationService notificationService,
            AvailabilityService availabilityService,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            AppointmentEventPublisher appointmentEventPublisher
    ) {
        this.appointmentRepository = appointmentRepository;
        this.slotReservationRepository = slotReservationRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.notificationService = notificationService;
        this.availabilityService = availabilityService;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.appointmentEventPublisher = appointmentEventPublisher;
    }

    public AppointmentServiceImpl(
            AppointmentRepository appointmentRepository,
            AppointmentSlotReservationRepository slotReservationRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            NotificationService notificationService,
            AvailabilityService availabilityService,
            UserRepository userRepository,
            MongoTemplate mongoTemplate
    ) {
        this(appointmentRepository, slotReservationRepository, doctorRepository,
                patientRepository, notificationService, availabilityService, userRepository, mongoTemplate, null);
    }

    @Override
    public Appointment createAppointment(AppointmentRequest request) {
        // 1. Resolve canonical Doctor profile identifier
        Doctor selectedDoctor = doctorRepository.findById(request.getDoctorId())
                .or(() -> doctorRepository.findByUserId(request.getDoctorId()))
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        String canonicalDoctorId = selectedDoctor.getId();

        // 2. Resolve or create patient profile
        patientRepository.findById(request.getPatientId()).orElseGet(() -> {
            User patientUser = userRepository.findById(request.getPatientId())
                    .filter(user -> user.getRole() == Role.PATIENT)
                    .orElseThrow(() -> new BadRequestException("Patient account not found"));

            Patient patient = Patient.builder()
                    .userId(patientUser.getId())
                    .fullName(patientUser.getFullName())
                    .email(patientUser.getEmail())
                    .phone(patientUser.getPhoneNumber())
                    .address(patientUser.getAddress())
                    .build();
            return patientRepository.save(patient);
        });

        // 3. Normalize slot identity parameters
        String normalizedDate = request.getAppointmentDate().trim();
        String normalizedTime = request.getAppointmentTime().trim().toUpperCase(Locale.ROOT);
        String slotKey = canonicalDoctorId + "_" + normalizedDate + "_" + normalizedTime;

        // 4. Verify schedule availability configured
        AvailableSlotsResponse slotAvailability = availabilityService.getAvailableSlots(
                canonicalDoctorId, normalizedDate);
        if (!slotAvailability.isScheduleConfigured()) {
            throw new BadRequestException("This doctor has not configured a schedule for the selected date");
        }
        if (!slotAvailability.isAvailable()) {
            throw new BadRequestException(slotAvailability.getReason() != null
                    ? "Doctor unavailable: " + slotAvailability.getReason()
                    : "Doctor is unavailable on the selected date");
        }
        if (!slotAvailability.getAvailableSlots().contains(request.getAppointmentTime())) {
            throw new AppointmentSlotConflictException("This slot is no longer available. Please select another time.");
        }

        // 5. Database-enforced atomic slot reservation
        AppointmentSlotReservation reservation = AppointmentSlotReservation.builder()
                .id(slotKey)
                .doctorId(canonicalDoctorId)
                .appointmentDate(normalizedDate)
                .appointmentTime(normalizedTime)
                .patientId(request.getPatientId())
                .status("RESERVED")
                .createdAt(Instant.now())
                .build();

        try {
            slotReservationRepository.insert(reservation);
        } catch (DuplicateKeyException ex) {
            log.warn("Slot conflict: Doctor {} slot on {} at {} already reserved", canonicalDoctorId, normalizedDate, normalizedTime);
            throw new AppointmentSlotConflictException("This slot is no longer available. Please select another time.");
        }

        // 6. Database-enforced atomic daily queue counter generation
        int nextTokenNumber;
        try {
            String counterKey = canonicalDoctorId + "_" + normalizedDate;
            Query counterQuery = Query.query(Criteria.where("_id").is(counterKey));
            Update counterUpdate = new Update()
                    .inc("seq", 1)
                    .setOnInsert("doctorId", canonicalDoctorId)
                    .setOnInsert("date", normalizedDate)
                    .set("updatedAt", Instant.now());
            FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
            DailyQueueCounter counter = mongoTemplate.findAndModify(counterQuery, counterUpdate, options, DailyQueueCounter.class);
            nextTokenNumber = (counter != null && counter.getSeq() != null) ? counter.getSeq().intValue() : 1;
        } catch (Exception ex) {
            log.error("Failed to generate atomic queue token for doctor {} on {}. Rolling back slot reservation.", canonicalDoctorId, normalizedDate, ex);
            slotReservationRepository.deleteById(slotKey);
            throw ex;
        }

        // 7. Persist Appointment and link to slot reservation
        Appointment appointment = Appointment.builder()
                .doctorId(canonicalDoctorId)
                .patientId(request.getPatientId())
                .appointmentDate(normalizedDate)
                .appointmentTime(request.getAppointmentTime().trim())
                .tokenNumber(nextTokenNumber)
                .reason(request.getReason())
                .status("PENDING")
                .build();

        Appointment savedAppointment;
        try {
            savedAppointment = appointmentRepository.save(appointment);

            // Link reservation with actual appointment ID
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(slotKey)),
                    new Update().set("appointmentId", savedAppointment.getId()),
                    AppointmentSlotReservation.class
            );
        } catch (Exception ex) {
            log.error("Failed to persist appointment. Rolling back slot reservation for slotKey {}", slotKey, ex);
            slotReservationRepository.deleteById(slotKey);
            throw ex;
        }

        // 8. Notifications & Domain Events (non-blocking for appointment persistence)
        try {
            if (appointmentEventPublisher != null) {
                appointmentEventPublisher.publishAppointmentCreated(savedAppointment);
            }
        } catch (Exception ex) {
            log.warn("Failed to publish AppointmentCreated domain event: {}", ex.getMessage());
        }

        try {
            notificationService.notifyAppointmentCreated(
                    savedAppointment.getId(),
                    savedAppointment.getPatientId(),
                    selectedDoctor.getUserId() != null ? selectedDoctor.getUserId() : savedAppointment.getDoctorId(),
                    savedAppointment.getAppointmentDate() + " " + savedAppointment.getAppointmentTime()
            );
        } catch (Exception e) {
            log.warn("Failed to send appointment notification: {}", e.getMessage());
        }

        return savedAppointment;
    }

    @Override
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Override
    public Appointment getAppointmentById(String id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
    }

    @Override
    public List<Appointment> getAppointmentsByDoctorId(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return List.of();
        }
        java.util.Set<String> doctorIds = new java.util.HashSet<>();
        doctorIds.add(doctorId);
        doctorRepository.findById(doctorId).ifPresent(d -> {
            if (d.getId() != null) doctorIds.add(d.getId());
            if (d.getUserId() != null) doctorIds.add(d.getUserId());
        });
        doctorRepository.findByUserId(doctorId).ifPresent(d -> {
            if (d.getId() != null) doctorIds.add(d.getId());
            if (d.getUserId() != null) doctorIds.add(d.getUserId());
        });

        if (doctorIds.size() > 1) {
            return appointmentRepository.findByDoctorIdIn(List.copyOf(doctorIds));
        }
        return appointmentRepository.findByDoctorId(doctorId);
    }

    @Override
    public List<Appointment> getAppointmentsByPatientId(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return List.of();
        }
        java.util.Set<String> patientIds = new java.util.LinkedHashSet<>();
        patientIds.add(patientId);

        patientRepository.findById(patientId).ifPresent(p -> {
            if (p.getId() != null) patientIds.add(p.getId());
            if (p.getUserId() != null) patientIds.add(p.getUserId());
        });
        patientRepository.findByUserId(patientId).ifPresent(p -> {
            if (p.getId() != null) patientIds.add(p.getId());
            if (p.getUserId() != null) patientIds.add(p.getUserId());
        });

        if (patientIds.size() > 1) {
            return appointmentRepository.findByPatientIdIn(List.copyOf(patientIds));
        }
        return appointmentRepository.findByPatientId(patientId);
    }

    @Override
    public List<Appointment> getMyAppointments(String patientId) {
        return getAppointmentsByPatientId(patientId);
    }

    @Override
    public Appointment updateAppointmentStatus(String id, String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Status cannot be empty");
        }
        Appointment appointment = getAppointmentById(id);
        String currentStatus = appointment.getStatus() != null ? appointment.getStatus().trim().toUpperCase(Locale.ROOT) : "PENDING";
        String targetStatus = status.trim().toUpperCase(Locale.ROOT);

        // 1. Idempotency: If status is already targetStatus, return safely
        if (currentStatus.equals(targetStatus)) {
            return appointment;
        }

        // 2. Validate state transitions
        validateStateTransition(currentStatus, targetStatus);

        appointment.setStatus(targetStatus);
        Appointment updatedAppointment = appointmentRepository.save(appointment);

        // 3. Slot reservation lifecycle: release reservation on cancellation or rejection
        if ("CANCELLED".equals(targetStatus) || "REJECTED".equals(targetStatus)) {
            releaseSlotReservation(appointment);
        }

        // 4. Domain Events & Notifications on valid status change
        try {
            if (appointmentEventPublisher != null) {
                appointmentEventPublisher.publishAppointmentStatusChanged(updatedAppointment, currentStatus, targetStatus);
            }
        } catch (Exception ex) {
            log.warn("Failed to publish appointment status change domain event: {}", ex.getMessage());
        }

        try {
            if ("CANCELLED".equals(targetStatus)) {
                notificationService.notifyAppointmentCancelled(
                        appointment.getId(),
                        appointment.getPatientId(),
                        appointment.getDoctorId()
                );
            } else if ("COMPLETED".equals(targetStatus)) {
                notificationService.notifyAppointmentUpdated(
                        appointment.getId(),
                        appointment.getPatientId(),
                        appointment.getDoctorId()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send status update notification: {}", e.getMessage());
        }

        return updatedAppointment;
    }

    @Override
    public void deleteAppointment(String id) {
        Appointment appointment = getAppointmentById(id);
        releaseSlotReservation(appointment);
        appointmentRepository.deleteById(id);
    }

    private void validateStateTransition(String currentStatus, String targetStatus) {
        if ("CANCELLED".equals(currentStatus)) {
            throw new InvalidBookingTransitionException("Cannot modify status of a cancelled appointment");
        }
        if ("COMPLETED".equals(currentStatus)) {
            throw new InvalidBookingTransitionException("Cannot modify status of a completed appointment");
        }
        if ("REJECTED".equals(currentStatus)) {
            throw new InvalidBookingTransitionException("Cannot modify status of a rejected appointment");
        }

        switch (currentStatus) {
            case "PENDING":
                if (!"CONFIRMED".equals(targetStatus) && !"APPROVED".equals(targetStatus)
                        && !"REJECTED".equals(targetStatus) && !"CANCELLED".equals(targetStatus)) {
                    throw new InvalidBookingTransitionException("Cannot transition appointment from PENDING to " + targetStatus);
                }
                break;
            case "CONFIRMED":
            case "APPROVED":
            case "SCHEDULED":
                if (!"COMPLETED".equals(targetStatus) && !"CANCELLED".equals(targetStatus) && !"NO_SHOW".equals(targetStatus)) {
                    throw new InvalidBookingTransitionException("Cannot transition appointment from " + currentStatus + " to " + targetStatus);
                }
                break;
            default:
                break;
        }
    }

    private void releaseSlotReservation(Appointment appointment) {
        if (appointment == null) return;
        try {
            // Delete reservation specifically bound to this appointment ID
            slotReservationRepository.deleteByAppointmentId(appointment.getId());

            // Also delete by compound key if matching appointmentId
            if (appointment.getDoctorId() != null && appointment.getAppointmentDate() != null && appointment.getAppointmentTime() != null) {
                String slotKey = appointment.getDoctorId() + "_" + appointment.getAppointmentDate().trim() + "_" + appointment.getAppointmentTime().trim().toUpperCase(Locale.ROOT);
                mongoTemplate.remove(
                        Query.query(Criteria.where("_id").is(slotKey).and("appointmentId").is(appointment.getId())),
                        AppointmentSlotReservation.class
                );
            }
            log.info("Released slot reservation for appointment {}", appointment.getId());
        } catch (Exception ex) {
            log.error("Error releasing slot reservation for appointment {}", appointment.getId(), ex);
        }
    }
}
