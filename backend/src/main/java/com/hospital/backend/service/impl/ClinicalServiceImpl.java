package com.hospital.backend.service.impl;

import com.hospital.backend.constant.ErrorCodes;
import com.hospital.backend.dto.MedicalRecordDTO;
import com.hospital.backend.dto.clinical.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.ClinicalRecordConflictException;
import com.hospital.backend.exception.InvalidVitalObservationException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.ClinicalAuditService;
import com.hospital.backend.service.ClinicalService;
import com.hospital.backend.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalServiceImpl implements ClinicalService {

    private final ClinicalEncounterRepository encounterRepository;
    private final ClinicalNoteRevisionRepository noteRevisionRepository;
    private final ClinicalObservationRepository observationRepository;
    private final PatientClinicalProfileRepository profileRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final AuthorizationService authz;
    private final ClinicalAuditService auditService;
    private final PatientService patientService;

    @Override
    public EncounterResponseDTO startOrResumeEncounter(CreateEncounterRequest request) {
        User currentUser = authz.getCurrentUser();
        if (currentUser.getRole() != Role.DOCTOR && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only clinicians can initiate or manage clinical encounters");
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + request.getAppointmentId()));

        if ("CANCELLED".equalsIgnoreCase(appointment.getStatus()) || "REJECTED".equalsIgnoreCase(appointment.getStatus())) {
            throw new ClinicalRecordConflictException(ErrorCodes.INVALID_ENCOUNTER_TRANSITION,
                    "Cannot start encounter for a cancelled or rejected appointment");
        }

        if (currentUser.getRole() == Role.DOCTOR && !authz.isDoctorOwner(currentUser, appointment.getDoctorId())) {
            throw new AccessDeniedException("Doctor is not assigned to this appointment");
        }

        // Check if an encounter already exists for this appointment
        Optional<ClinicalEncounter> existingOpt = encounterRepository.findByAppointmentId(appointment.getId());
        if (existingOpt.isPresent()) {
            ClinicalEncounter existing = existingOpt.get();
            if (existing.getStatus() == EncounterStatus.SIGNED || existing.getStatus() == EncounterStatus.AMENDED) {
                throw new ClinicalRecordConflictException(ErrorCodes.ENCOUNTER_ALREADY_EXISTS,
                        "A finalized clinical encounter already exists for this appointment");
            }
            // Resume existing DRAFT or IN_PROGRESS encounter
            log.info("Resuming existing active clinical encounter {} for appointment {}", existing.getId(), appointment.getId());
            return buildEncounterResponse(existing);
        }

        // Resolve verified patient and doctor IDs
        String canonicalPatientId = resolveCanonicalPatientId(appointment.getPatientId());
        String canonicalDoctorId = resolveCanonicalDoctorId(appointment.getDoctorId(), currentUser);

        Instant now = Instant.now();
        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .appointmentId(appointment.getId())
                .patientId(canonicalPatientId)
                .doctorId(canonicalDoctorId)
                .encounterType(request.getEncounterType() != null ? request.getEncounterType() : EncounterType.OUTPATIENT)
                .status(EncounterStatus.IN_PROGRESS)
                .visitStartedAt(now)
                .chiefComplaint(request.getChiefComplaint() != null ? request.getChiefComplaint() : appointment.getReason())
                .historyOfPresentIllness(request.getHistoryOfPresentIllness())
                .createdByUserId(currentUser.getId())
                .updatedByUserId(currentUser.getId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        ClinicalEncounter savedEncounter = encounterRepository.save(encounter);

        // Create initial draft note revision
        ClinicalNoteRevision initialDraft = ClinicalNoteRevision.builder()
                .encounterId(savedEncounter.getId())
                .patientId(canonicalPatientId)
                .doctorId(canonicalDoctorId)
                .revisionNumber(1)
                .status(NoteStatus.DRAFT)
                .chiefComplaint(savedEncounter.getChiefComplaint())
                .createdAt(now)
                .updatedAt(now)
                .build();
        noteRevisionRepository.save(initialDraft);

        auditService.audit(canonicalPatientId, "ENCOUNTER", savedEncounter.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_CREATED, "SUCCESS", null, "Encounter started");

        return buildEncounterResponse(savedEncounter);
    }

    @Override
    public EncounterResponseDTO getEncounterById(String id) {
        ClinicalEncounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        authz.checkEncounterAccess(encounter);

        auditService.audit(encounter.getPatientId(), "ENCOUNTER", encounter.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_VIEWED, "SUCCESS", null, "Viewed encounter");

        return buildEncounterResponse(encounter);
    }

    @Override
    public List<EncounterResponseDTO> getPatientEncounters(String patientId) {
        authz.checkPatientAccess(patientId);
        User currentUser = authz.getCurrentUser();

        String canonicalPatientId = resolveCanonicalPatientId(patientId);
        List<ClinicalEncounter> encounters = encounterRepository.findByPatientIdOrderByVisitStartedAtDesc(canonicalPatientId);

        // Patients can only view released / signed / amended records
        if (currentUser.getRole() == Role.PATIENT) {
            encounters = encounters.stream()
                    .filter(e -> e.getStatus() == EncounterStatus.SIGNED || e.getStatus() == EncounterStatus.AMENDED)
                    .collect(Collectors.toList());
        }

        auditService.audit(canonicalPatientId, "ENCOUNTER", null,
                ClinicalAuditAction.CLINICAL_RECORD_VIEWED, "SUCCESS", null, "List patient encounters");

        return encounters.stream().map(this::buildEncounterResponse).collect(Collectors.toList());
    }

    @Override
    public EncounterResponseDTO updateEncounterDraft(String id, UpdateEncounterDraftRequest request) {
        ClinicalEncounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + id));

        authz.checkEncounterAccess(encounter);
        User currentUser = authz.getCurrentUser();
        if (currentUser.getRole() == Role.PATIENT) {
            throw new AccessDeniedException("Patients cannot edit clinician encounters");
        }

        if (encounter.getStatus() == EncounterStatus.SIGNED || encounter.getStatus() == EncounterStatus.AMENDED) {
            throw new ClinicalRecordConflictException(ErrorCodes.SIGNED_NOTE_IMMUTABLE,
                    "Signed clinical encounters cannot be directly modified. Use the amendment workflow.");
        }

        if (request.getExpectedVersion() != null && !request.getExpectedVersion().equals(encounter.getVersion())) {
            throw new ClinicalRecordConflictException(ErrorCodes.CLINICAL_RECORD_VERSION_CONFLICT,
                    "Encounter was modified by another user. Reload and try again.");
        }

        if (request.getChiefComplaint() != null) encounter.setChiefComplaint(request.getChiefComplaint());
        if (request.getHistoryOfPresentIllness() != null) encounter.setHistoryOfPresentIllness(request.getHistoryOfPresentIllness());
        if (request.getClinicalSummary() != null) encounter.setClinicalSummary(request.getClinicalSummary());

        encounter.setUpdatedByUserId(currentUser.getId());
        encounter.setUpdatedAt(Instant.now());

        ClinicalEncounter saved = encounterRepository.save(encounter);

        auditService.audit(saved.getPatientId(), "ENCOUNTER", saved.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_UPDATED, "SUCCESS", null, "Updated encounter draft");

        return buildEncounterResponse(saved);
    }

    @Override
    public EncounterResponseDTO saveSoapNoteDraft(String encounterId, SaveSoapNoteDraftRequest request) {
        ClinicalEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        authz.checkEncounterAccess(encounter);
        User currentUser = authz.getCurrentUser();
        if (currentUser.getRole() == Role.PATIENT) {
            throw new AccessDeniedException("Patients cannot edit clinical consultation notes");
        }

        if (encounter.getStatus() == EncounterStatus.SIGNED || encounter.getStatus() == EncounterStatus.AMENDED) {
            throw new ClinicalRecordConflictException(ErrorCodes.SIGNED_NOTE_IMMUTABLE,
                    "Signed consultation notes are immutable. Use the amendment workflow.");
        }

        ClinicalNoteRevision draftNote = noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc(encounterId)
                .orElseGet(() -> ClinicalNoteRevision.builder()
                        .encounterId(encounterId)
                        .patientId(encounter.getPatientId())
                        .doctorId(encounter.getDoctorId())
                        .revisionNumber(1)
                        .status(NoteStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build());

        if (draftNote.getStatus() == NoteStatus.SIGNED || draftNote.getStatus() == NoteStatus.AMENDED) {
            throw new ClinicalRecordConflictException(ErrorCodes.SIGNED_NOTE_IMMUTABLE,
                    "Active note revision is already finalized");
        }

        if (request.getExpectedVersion() != null && !request.getExpectedVersion().equals(draftNote.getVersion())) {
            throw new ClinicalRecordConflictException(ErrorCodes.CLINICAL_RECORD_VERSION_CONFLICT,
                    "Note revision was modified by another session. Reload and try again.");
        }

        mapSoapFields(request, draftNote);
        draftNote.setUpdatedAt(Instant.now());
        noteRevisionRepository.save(draftNote);

        auditService.audit(encounter.getPatientId(), "NOTE", draftNote.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_UPDATED, "SUCCESS", null, "Updated SOAP note draft");

        return buildEncounterResponse(encounter);
    }

    @Override
    public EncounterResponseDTO signEncounter(String encounterId, SignEncounterRequest request) {
        ClinicalEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        authz.checkEncounterSigning(encounter);
        User currentUser = authz.getCurrentUser();

        if (encounter.getStatus() == EncounterStatus.SIGNED || encounter.getStatus() == EncounterStatus.AMENDED) {
            throw new ClinicalRecordConflictException(ErrorCodes.SIGNED_NOTE_IMMUTABLE,
                    "Encounter is already signed and finalized");
        }

        if (request.getExpectedEncounterVersion() != null && !request.getExpectedEncounterVersion().equals(encounter.getVersion())) {
            throw new ClinicalRecordConflictException(ErrorCodes.CLINICAL_RECORD_VERSION_CONFLICT,
                    "Encounter version conflict. Reload before signing.");
        }

        Instant now = Instant.now();
        String doctorName = resolveDoctorName(currentUser, encounter.getDoctorId());

        // Update or create and sign the active note revision
        ClinicalNoteRevision note = noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc(encounterId)
                .orElseGet(() -> ClinicalNoteRevision.builder()
                        .encounterId(encounterId)
                        .patientId(encounter.getPatientId())
                        .doctorId(encounter.getDoctorId())
                        .revisionNumber(1)
                        .createdAt(now)
                        .build());

        if (request.getNoteData() != null) {
            mapSoapFields(request.getNoteData(), note);
        }

        note.setStatus(NoteStatus.SIGNED);
        note.setSignedAt(now);
        note.setSignedByUserId(currentUser.getId());
        note.setSignedByDoctorName(doctorName);
        note.setUpdatedAt(now);
        noteRevisionRepository.save(note);

        // Finalize encounter
        encounter.setStatus(EncounterStatus.SIGNED);
        encounter.setSignedAt(now);
        encounter.setSignedByUserId(currentUser.getId());
        encounter.setSignedByDoctorName(doctorName);
        encounter.setVisitCompletedAt(now);
        encounter.setUpdatedAt(now);
        encounter.setUpdatedByUserId(currentUser.getId());

        ClinicalEncounter signedEncounter = encounterRepository.save(encounter);

        // Publish durable outbox notification without PHI
        publishOutboxNotification(signedEncounter, "Clinical Record Finalized",
                "Your doctor has finalized a consultation record for your appointment.");

        auditService.audit(signedEncounter.getPatientId(), "ENCOUNTER", signedEncounter.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_SIGNED, "SUCCESS", null, "Signed and finalized encounter");

        return buildEncounterResponse(signedEncounter);
    }

    @Override
    public EncounterResponseDTO amendEncounter(String encounterId, AmendEncounterRequest request) {
        ClinicalEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));

        authz.checkEncounterSigning(encounter);
        User currentUser = authz.getCurrentUser();

        if (encounter.getStatus() != EncounterStatus.SIGNED && encounter.getStatus() != EncounterStatus.AMENDED) {
            throw new ClinicalRecordConflictException(ErrorCodes.INVALID_ENCOUNTER_TRANSITION,
                    "Cannot amend an encounter that has not yet been signed. Please edit the draft directly.");
        }

        if (request.getAmendmentReason() == null || request.getAmendmentReason().trim().isEmpty()) {
            throw new BadRequestException("Amendment reason is mandatory for clinical record addenda");
        }

        if (request.getExpectedEncounterVersion() != null && !request.getExpectedEncounterVersion().equals(encounter.getVersion())) {
            throw new ClinicalRecordConflictException(ErrorCodes.CLINICAL_RECORD_VERSION_CONFLICT,
                    "Encounter was modified concurrently. Please reload before amending.");
        }

        Instant now = Instant.now();
        String doctorName = resolveDoctorName(currentUser, encounter.getDoctorId());

        // Find previous revision to copy from
        ClinicalNoteRevision previousRev = noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("No previous note revision found for amendment"));

        int nextRevisionNumber = previousRev.getRevisionNumber() + 1;

        ClinicalNoteRevision amendmentNote = ClinicalNoteRevision.builder()
                .encounterId(encounterId)
                .patientId(encounter.getPatientId())
                .doctorId(encounter.getDoctorId())
                .revisionNumber(nextRevisionNumber)
                .status(NoteStatus.AMENDED)
                .amendmentReason(request.getAmendmentReason().trim())
                .amendedByUserId(currentUser.getId())
                .amendedAt(now)
                .signedAt(now)
                .signedByUserId(currentUser.getId())
                .signedByDoctorName(doctorName)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Copy previous values as baseline
        amendmentNote.setChiefComplaint(previousRev.getChiefComplaint());
        amendmentNote.setSubjective(previousRev.getSubjective());
        amendmentNote.setObjective(previousRev.getObjective());
        amendmentNote.setVitalsSummary(previousRev.getVitalsSummary());
        amendmentNote.setAssessment(previousRev.getAssessment());
        amendmentNote.setDiagnosisText(previousRev.getDiagnosisText());
        amendmentNote.setDiagnosisCode(previousRev.getDiagnosisCode());
        amendmentNote.setCodingSystem(previousRev.getCodingSystem());
        amendmentNote.setDiagnosisStatus(previousRev.getDiagnosisStatus());
        amendmentNote.setPlan(previousRev.getPlan());
        amendmentNote.setTestsRequested(previousRev.getTestsRequested());
        amendmentNote.setFollowUpInstructions(previousRev.getFollowUpInstructions());
        amendmentNote.setFollowUpDate(previousRev.getFollowUpDate());
        amendmentNote.setFollowUpRequired(previousRev.getFollowUpRequired());
        amendmentNote.setReferralNotes(previousRev.getReferralNotes());

        // Apply new values if provided
        if (request.getNoteData() != null) {
            mapSoapFields(request.getNoteData(), amendmentNote);
        }

        noteRevisionRepository.save(amendmentNote);

        encounter.setStatus(EncounterStatus.AMENDED);
        encounter.setUpdatedAt(now);
        encounter.setUpdatedByUserId(currentUser.getId());
        ClinicalEncounter amendedEncounter = encounterRepository.save(encounter);

        publishOutboxNotification(amendedEncounter, "Clinical Record Amended",
                "An authorized amendment was made to your consultation record.");

        auditService.audit(amendedEncounter.getPatientId(), "ENCOUNTER", amendedEncounter.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_AMENDED, "SUCCESS", null,
                "Encounter amended rev " + nextRevisionNumber + ": " + request.getAmendmentReason());

        return buildEncounterResponse(amendedEncounter);
    }

    @Override
    public ClinicalObservation recordVitals(RecordVitalsRequest request) {
        User currentUser = authz.getCurrentUser();
        if (currentUser.getRole() != Role.DOCTOR && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only clinical staff may record vital signs");
        }

        String patientId;
        if (request.getEncounterId() != null) {
            ClinicalEncounter encounter = encounterRepository.findById(request.getEncounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + request.getEncounterId()));
            authz.checkEncounterAccess(encounter);
            patientId = encounter.getPatientId();
        } else {
            throw new BadRequestException("encounterId is required to record vital signs");
        }

        // Validate plausible biological ranges
        validateVitals(request);

        ClinicalObservation observation = ClinicalObservation.builder()
                .encounterId(request.getEncounterId())
                .patientId(patientId)
                .observedAt(Instant.now())
                .recordedByUserId(currentUser.getId())
                .recordedByName(currentUser.getFullName())
                .systolicBp(request.getSystolicBp())
                .diastolicBp(request.getDiastolicBp())
                .heartRate(request.getHeartRate())
                .temperature(request.getTemperature())
                .respiratoryRate(request.getRespiratoryRate())
                .oxygenSaturation(request.getOxygenSaturation())
                .weightKg(request.getWeightKg())
                .heightCm(request.getHeightCm())
                .painScore(request.getPainScore())
                .clinicianNotes(request.getClinicianNotes())
                .build();

        ClinicalObservation saved = observationRepository.save(observation);

        auditService.audit(patientId, "VITALS", saved.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_CREATED, "SUCCESS", null, "Recorded vital signs");

        return saved;
    }

    @Override
    public List<ClinicalObservation> getEncounterVitals(String encounterId) {
        ClinicalEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter not found: " + encounterId));
        authz.checkEncounterAccess(encounter);

        return observationRepository.findByEncounterIdOrderByObservedAtDesc(encounterId);
    }

    @Override
    public List<ClinicalObservation> getPatientVitals(String patientId) {
        authz.checkPatientAccess(patientId);
        String canonicalPatientId = resolveCanonicalPatientId(patientId);
        return observationRepository.findByPatientIdOrderByObservedAtDesc(canonicalPatientId);
    }

    @Override
    public PatientClinicalProfile getPatientProfile(String patientId) {
        authz.checkPatientAccess(patientId);
        String canonicalPatientId = resolveCanonicalPatientId(patientId);

        return profileRepository.findByPatientId(canonicalPatientId)
                .orElseGet(() -> PatientClinicalProfile.builder()
                        .patientId(canonicalPatientId)
                        .allergyStatus(AllergyStatus.UNKNOWN)
                        .allergies(new ArrayList<>())
                        .pastMedicalHistory(new ArrayList<>())
                        .pastSurgicalHistory(new ArrayList<>())
                        .chronicConditions(new ArrayList<>())
                        .familyMedicalHistory(new ArrayList<>())
                        .socialHistory(new ArrayList<>())
                        .currentMedications(new ArrayList<>())
                        .build());
    }

    @Override
    public PatientClinicalProfile updatePatientProfile(String patientId, UpdateClinicalProfileRequest request) {
        String canonicalPatientId = resolveCanonicalPatientId(patientId);
        if (!authz.canModifyClinicalProfile(canonicalPatientId)) {
            throw new AccessDeniedException("Only authorized clinical staff can modify patient clinical profiles");
        }

        User currentUser = authz.getCurrentUser();
        PatientClinicalProfile profile = profileRepository.findByPatientId(canonicalPatientId)
                .orElseGet(() -> PatientClinicalProfile.builder()
                        .patientId(canonicalPatientId)
                        .allergyStatus(AllergyStatus.UNKNOWN)
                        .build());

        if (request.getExpectedVersion() != null && profile.getVersion() != null
                && !request.getExpectedVersion().equals(profile.getVersion())) {
            throw new ClinicalRecordConflictException(ErrorCodes.CLINICAL_RECORD_VERSION_CONFLICT,
                    "Clinical profile was modified concurrently. Please reload and try again.");
        }

        if (request.getAllergyStatus() != null) {
            profile.setAllergyStatus(request.getAllergyStatus());
        }
        if (request.getAllergies() != null) {
            profile.setAllergies(request.getAllergies());
        }
        if (request.getPastMedicalHistory() != null) {
            profile.setPastMedicalHistory(request.getPastMedicalHistory());
        }
        if (request.getPastSurgicalHistory() != null) {
            profile.setPastSurgicalHistory(request.getPastSurgicalHistory());
        }
        if (request.getChronicConditions() != null) {
            profile.setChronicConditions(request.getChronicConditions());
        }
        if (request.getFamilyMedicalHistory() != null) {
            profile.setFamilyMedicalHistory(request.getFamilyMedicalHistory());
        }
        if (request.getSocialHistory() != null) {
            profile.setSocialHistory(request.getSocialHistory());
        }
        if (request.getCurrentMedications() != null) {
            profile.setCurrentMedications(request.getCurrentMedications());
        }

        profile.setUpdatedAt(Instant.now());
        profile.setUpdatedByUserId(currentUser.getId());
        profile.setUpdatedByUserName(currentUser.getFullName());

        PatientClinicalProfile saved = profileRepository.save(profile);

        auditService.audit(canonicalPatientId, "PROFILE", saved.getId(),
                ClinicalAuditAction.CLINICAL_RECORD_UPDATED, "SUCCESS", null, "Updated clinical profile");

        return saved;
    }

    @Override
    public List<MedicalRecordDTO> getClinicalTimeline(String patientId) {
        authz.checkPatientAccess(patientId);
        User currentUser = authz.getCurrentUser();
        String canonicalPatientId = resolveCanonicalPatientId(patientId);

        // Fetch baseline medical history (Appointments, Prescriptions, Labs)
        List<MedicalRecordDTO> timeline = new ArrayList<>(patientService.getMedicalHistory(canonicalPatientId));

        // Fetch encounters
        List<ClinicalEncounter> encounters = encounterRepository.findByPatientIdOrderByVisitStartedAtDesc(canonicalPatientId);

        for (ClinicalEncounter encounter : encounters) {
            // Patients only see finalized encounters
            if (currentUser.getRole() == Role.PATIENT &&
                    (encounter.getStatus() == EncounterStatus.DRAFT || encounter.getStatus() == EncounterStatus.IN_PROGRESS)) {
                continue;
            }

            Optional<ClinicalNoteRevision> latestNoteOpt = noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc(encounter.getId());
            String diagText = latestNoteOpt.map(ClinicalNoteRevision::getDiagnosisText).orElse(null);
            String docName = encounter.getSignedByDoctorName();
            if (docName == null) {
                Doctor doc = doctorRepository.findById(encounter.getDoctorId())
                        .or(() -> doctorRepository.findByUserId(encounter.getDoctorId()))
                        .orElse(null);
                docName = doc != null ? doc.getFullName() : "Assigned Clinician";
            }

            String dateStr = encounter.getVisitStartedAt() != null
                    ? DateTimeFormatter.ISO_INSTANT.format(encounter.getVisitStartedAt()).split("T")[0]
                    : "Unknown";

            timeline.add(MedicalRecordDTO.builder()
                    .id(encounter.getId())
                    .date(dateStr)
                    .type("CLINICAL_ENCOUNTER")
                    .title("Clinical Encounter (" + encounter.getEncounterType() + ")")
                    .description(diagText != null ? "Diagnosis: " + diagText : (encounter.getChiefComplaint() != null ? encounter.getChiefComplaint() : "Consultation"))
                    .doctorName(docName)
                    .status(encounter.getStatus().name())
                    .encounterId(encounter.getId())
                    .diagnosis(diagText)
                    .signedAt(encounter.getSignedAt() != null ? encounter.getSignedAt().toString() : null)
                    .build());
        }

        // Sort descending by date
        timeline.sort(Comparator.comparing(MedicalRecordDTO::getDate, Comparator.nullsLast(Comparator.reverseOrder())));

        auditService.audit(canonicalPatientId, "TIMELINE", null,
                ClinicalAuditAction.CLINICAL_RECORD_VIEWED, "SUCCESS", null, "Viewed unified clinical timeline");

        return timeline;
    }

    private void validateVitals(RecordVitalsRequest r) {
        if (r.getSystolicBp() != null && (r.getSystolicBp() < 40 || r.getSystolicBp() > 300)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Systolic BP must be between 40 and 300 mmHg");
        }
        if (r.getDiastolicBp() != null && (r.getDiastolicBp() < 20 || r.getDiastolicBp() > 200)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Diastolic BP must be between 20 and 200 mmHg");
        }
        if (r.getSystolicBp() != null && r.getDiastolicBp() != null && r.getDiastolicBp() >= r.getSystolicBp()) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Diastolic BP cannot exceed or equal Systolic BP");
        }
        if (r.getHeartRate() != null && (r.getHeartRate() < 20 || r.getHeartRate() > 300)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Heart rate must be between 20 and 300 bpm");
        }
        if (r.getTemperature() != null && (r.getTemperature() < 30.0 || r.getTemperature() > 45.0)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Temperature must be between 30.0 and 45.0 °C");
        }
        if (r.getRespiratoryRate() != null && (r.getRespiratoryRate() < 5 || r.getRespiratoryRate() > 100)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Respiratory rate must be between 5 and 100 breaths/min");
        }
        if (r.getOxygenSaturation() != null && (r.getOxygenSaturation() < 50 || r.getOxygenSaturation() > 100)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Oxygen saturation (SpO2) must be between 50 and 100 %");
        }
        if (r.getWeightKg() != null && (r.getWeightKg() < 0.5 || r.getWeightKg() > 500.0)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Weight must be between 0.5 and 500.0 kg");
        }
        if (r.getHeightCm() != null && (r.getHeightCm() < 20.0 || r.getHeightCm() > 300.0)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Height must be between 20.0 and 300.0 cm");
        }
        if (r.getPainScore() != null && (r.getPainScore() < 0 || r.getPainScore() > 10)) {
            throw new InvalidVitalObservationException(ErrorCodes.INVALID_VITAL_OBSERVATION, "Pain score must be between 0 and 10");
        }
    }

    private void mapSoapFields(SaveSoapNoteDraftRequest req, ClinicalNoteRevision target) {
        if (req.getChiefComplaint() != null) target.setChiefComplaint(req.getChiefComplaint());
        if (req.getSubjective() != null) target.setSubjective(req.getSubjective());
        if (req.getObjective() != null) target.setObjective(req.getObjective());
        if (req.getAssessment() != null) target.setAssessment(req.getAssessment());
        if (req.getDiagnosisText() != null) target.setDiagnosisText(req.getDiagnosisText());
        if (req.getDiagnosisCode() != null) target.setDiagnosisCode(req.getDiagnosisCode());
        if (req.getCodingSystem() != null) target.setCodingSystem(req.getCodingSystem());
        if (req.getDiagnosisStatus() != null) target.setDiagnosisStatus(req.getDiagnosisStatus());
        if (req.getPlan() != null) target.setPlan(req.getPlan());
        if (req.getTestsRequested() != null) target.setTestsRequested(req.getTestsRequested());
        if (req.getFollowUpInstructions() != null) target.setFollowUpInstructions(req.getFollowUpInstructions());
        if (req.getFollowUpDate() != null) target.setFollowUpDate(req.getFollowUpDate());
        if (req.getFollowUpRequired() != null) target.setFollowUpRequired(req.getFollowUpRequired());
        if (req.getReferralNotes() != null) target.setReferralNotes(req.getReferralNotes());
    }

    private EncounterResponseDTO buildEncounterResponse(ClinicalEncounter encounter) {
        String patientName = "Patient";
        Optional<Patient> pOpt = patientRepository.findById(encounter.getPatientId())
                .or(() -> patientRepository.findByUserId(encounter.getPatientId()));
        if (pOpt.isPresent()) {
            patientName = pOpt.get().getFullName();
        }

        String doctorName = encounter.getSignedByDoctorName();
        if (doctorName == null) {
            Optional<Doctor> dOpt = doctorRepository.findById(encounter.getDoctorId())
                    .or(() -> doctorRepository.findByUserId(encounter.getDoctorId()));
            if (dOpt.isPresent()) {
                doctorName = dOpt.get().getFullName();
            } else {
                Optional<User> uOpt = userRepository.findById(encounter.getDoctorId());
                doctorName = uOpt.map(User::getFullName).orElse("Doctor");
            }
        }

        ClinicalNoteRevision currentNote = noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc(encounter.getId())
                .orElse(null);
        List<ClinicalNoteRevision> revisions = noteRevisionRepository.findByEncounterIdOrderByRevisionNumberAsc(encounter.getId());
        List<ClinicalObservation> observations = observationRepository.findByEncounterIdOrderByObservedAtDesc(encounter.getId());

        return EncounterResponseDTO.builder()
                .id(encounter.getId())
                .appointmentId(encounter.getAppointmentId())
                .patientId(encounter.getPatientId())
                .patientName(patientName)
                .doctorId(encounter.getDoctorId())
                .doctorName(doctorName)
                .encounterType(encounter.getEncounterType())
                .status(encounter.getStatus())
                .visitStartedAt(encounter.getVisitStartedAt())
                .visitCompletedAt(encounter.getVisitCompletedAt())
                .chiefComplaint(encounter.getChiefComplaint())
                .historyOfPresentIllness(encounter.getHistoryOfPresentIllness())
                .clinicalSummary(encounter.getClinicalSummary())
                .signedAt(encounter.getSignedAt())
                .signedByDoctorName(doctorName)
                .version(encounter.getVersion())
                .currentNote(currentNote)
                .revisions(revisions)
                .observations(observations)
                .build();
    }

    private String resolveCanonicalPatientId(String patientIdOrUserId) {
        if (patientIdOrUserId == null) return null;
        return patientRepository.findById(patientIdOrUserId)
                .map(Patient::getId)
                .orElseGet(() -> patientRepository.findByUserId(patientIdOrUserId)
                        .map(Patient::getId)
                        .orElse(patientIdOrUserId));
    }

    private String resolveCanonicalDoctorId(String doctorIdOrUserId, User currentUser) {
        if (doctorIdOrUserId != null) {
            return doctorRepository.findById(doctorIdOrUserId)
                    .map(Doctor::getId)
                    .orElseGet(() -> doctorRepository.findByUserId(doctorIdOrUserId)
                            .map(Doctor::getId)
                            .orElse(doctorIdOrUserId));
        }
        return currentUser != null ? currentUser.getId() : null;
    }

    private String resolveDoctorName(User currentUser, String doctorId) {
        if (currentUser.getRole() == Role.DOCTOR) {
            return currentUser.getFullName();
        }
        Doctor doc = doctorRepository.findById(doctorId)
                .or(() -> doctorRepository.findByUserId(doctorId))
                .orElse(null);
        return doc != null ? doc.getFullName() : currentUser.getFullName();
    }

    private void publishOutboxNotification(ClinicalEncounter encounter, String title, String message) {
        try {
            // Find patient userId for notification recipient
            String recipientUserId = encounter.getPatientId();
            Patient p = patientRepository.findById(encounter.getPatientId())
                    .or(() -> patientRepository.findByUserId(encounter.getPatientId()))
                    .orElse(null);
            if (p != null && p.getUserId() != null) {
                recipientUserId = p.getUserId();
            }

            Instant now = Instant.now();
            String dedupKey = "encounter:" + encounter.getId() + ":" + encounter.getStatus().name() + ":" + recipientUserId;

            NotificationOutbox outboxItem = NotificationOutbox.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("CLINICAL_RECORD_" + encounter.getStatus().name())
                    .aggregateId(encounter.getId())
                    .recipientUserId(recipientUserId)
                    .recipientEmail(p != null ? p.getEmail() : null)
                    .channel(OutboxChannel.IN_APP)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(3)
                    .createdAt(now)
                    .updatedAt(now)
                    .deduplicationKey(dedupKey)
                    .title(title)
                    .message(message)
                    .build();

            outboxRepository.save(outboxItem);
        } catch (Exception e) {
            log.warn("Failed to schedule clinical outbox notification: {}", e.getMessage());
        }
    }
}
