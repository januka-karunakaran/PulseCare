package com.hospital.backend.service;

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
import com.hospital.backend.service.impl.ClinicalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalServiceTest {

    @Mock private ClinicalEncounterRepository encounterRepository;
    @Mock private ClinicalNoteRevisionRepository noteRevisionRepository;
    @Mock private ClinicalObservationRepository observationRepository;
    @Mock private PatientClinicalProfileRepository profileRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private AuthorizationService authz;
    @Mock private ClinicalAuditService auditService;
    @Mock private PatientService patientService;

    @InjectMocks
    private ClinicalServiceImpl clinicalService;

    private User doctorUser;
    private User patientUser;
    private Appointment appointment;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        doctorUser = User.builder()
                .id("doc-user-1")
                .email("dr.smith@hospital.org")
                .fullName("Dr. John Smith")
                .role(Role.DOCTOR)
                .build();

        patientUser = User.builder()
                .id("pat-user-1")
                .email("jane.doe@email.com")
                .fullName("Jane Doe")
                .role(Role.PATIENT)
                .build();

        patient = Patient.builder()
                .id("patient-rec-1")
                .userId("pat-user-1")
                .fullName("Jane Doe")
                .email("jane.doe@email.com")
                .build();

        doctor = Doctor.builder()
                .id("doctor-rec-1")
                .userId("doc-user-1")
                .fullName("Dr. John Smith")
                .email("dr.smith@hospital.org")
                .build();

        appointment = Appointment.builder()
                .id("appt-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .appointmentDate("2026-05-10")
                .appointmentTime("10:00 AM")
                .status("APPROVED")
                .reason("Chest pain evaluation")
                .build();
    }

    // ==========================================
    // GROUP A: ENCOUNTERS
    // ==========================================

    @Test
    @DisplayName("A1: Authorized doctor creates new clinical encounter from appointment")
    void testAuthorizedDoctorCreatesEncounter() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(appointment));
        when(authz.isDoctorOwner(doctorUser, "doctor-rec-1")).thenReturn(true);
        when(encounterRepository.findByAppointmentId("appt-1")).thenReturn(Optional.empty());
        when(patientRepository.findById("patient-rec-1")).thenReturn(Optional.of(patient));
        when(doctorRepository.findById("doctor-rec-1")).thenReturn(Optional.of(doctor));

        ClinicalEncounter saved = ClinicalEncounter.builder()
                .id("enc-1")
                .appointmentId("appt-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.IN_PROGRESS)
                .version(1L)
                .build();
        when(encounterRepository.save(any(ClinicalEncounter.class))).thenReturn(saved);

        CreateEncounterRequest req = CreateEncounterRequest.builder()
                .appointmentId("appt-1")
                .encounterType(EncounterType.OUTPATIENT)
                .chiefComplaint("Severe chest discomfort")
                .build();

        EncounterResponseDTO response = clinicalService.startOrResumeEncounter(req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("enc-1");
        assertThat(response.getStatus()).isEqualTo(EncounterStatus.IN_PROGRESS);

        verify(noteRevisionRepository).save(any(ClinicalNoteRevision.class));
        verify(auditService).audit(eq("patient-rec-1"), eq("ENCOUNTER"), eq("enc-1"),
                eq(ClinicalAuditAction.CLINICAL_RECORD_CREATED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("A2: Unauthorized doctor denied creating encounter for unassigned appointment")
    void testUnauthorizedDoctorDeniedEncounterCreation() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(appointment));
        when(authz.isDoctorOwner(doctorUser, "doctor-rec-1")).thenReturn(false);

        CreateEncounterRequest req = CreateEncounterRequest.builder()
                .appointmentId("appt-1")
                .build();

        assertThrows(AccessDeniedException.class, () -> clinicalService.startOrResumeEncounter(req));
        verify(encounterRepository, never()).save(any());
    }

    @Test
    @DisplayName("A3: Cannot start encounter for cancelled appointment")
    void testCannotStartEncounterForCancelledAppointment() {
        appointment.setStatus("CANCELLED");
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(appointment));

        CreateEncounterRequest req = CreateEncounterRequest.builder()
                .appointmentId("appt-1")
                .build();

        assertThrows(ClinicalRecordConflictException.class, () -> clinicalService.startOrResumeEncounter(req));
    }

    @Test
    @DisplayName("A4: Resumes existing in-progress encounter instead of creating duplicate")
    void testResumesExistingInProgressEncounter() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(appointment));
        when(authz.isDoctorOwner(doctorUser, "doctor-rec-1")).thenReturn(true);

        ClinicalEncounter existing = ClinicalEncounter.builder()
                .id("enc-existing")
                .appointmentId("appt-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.IN_PROGRESS)
                .version(1L)
                .build();
        when(encounterRepository.findByAppointmentId("appt-1")).thenReturn(Optional.of(existing));

        CreateEncounterRequest req = CreateEncounterRequest.builder().appointmentId("appt-1").build();
        EncounterResponseDTO response = clinicalService.startOrResumeEncounter(req);

        assertThat(response.getId()).isEqualTo("enc-existing");
        verify(encounterRepository, never()).save(any());
    }

    @Test
    @DisplayName("A5: Fails to start new encounter if a signed encounter already exists")
    void testFailsIfSignedEncounterAlreadyExists() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(appointmentRepository.findById("appt-1")).thenReturn(Optional.of(appointment));
        when(authz.isDoctorOwner(doctorUser, "doctor-rec-1")).thenReturn(true);

        ClinicalEncounter signed = ClinicalEncounter.builder()
                .id("enc-signed")
                .appointmentId("appt-1")
                .status(EncounterStatus.SIGNED)
                .build();
        when(encounterRepository.findByAppointmentId("appt-1")).thenReturn(Optional.of(signed));

        CreateEncounterRequest req = CreateEncounterRequest.builder().appointmentId("appt-1").build();
        assertThrows(ClinicalRecordConflictException.class, () -> clinicalService.startOrResumeEncounter(req));
    }

    // ==========================================
    // GROUP B: PATIENT MEDICAL PROFILE
    // ==========================================

    @Test
    @DisplayName("B1: Clinician updates clinical profile with allergies and medical history")
    void testUpdateClinicalProfileSucceeds() {
        when(patientRepository.findById("patient-rec-1")).thenReturn(Optional.of(patient));
        when(authz.canModifyClinicalProfile("patient-rec-1")).thenReturn(true);
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(profileRepository.findByPatientId("patient-rec-1")).thenReturn(Optional.empty());

        PatientClinicalProfile.AllergyRecord allergy = PatientClinicalProfile.AllergyRecord.builder()
                .allergen("Penicillin")
                .reaction("Anaphylaxis")
                .severity(AllergySeverity.LIFE_THREATENING)
                .verificationStatus("VERIFIED")
                .build();

        UpdateClinicalProfileRequest req = UpdateClinicalProfileRequest.builder()
                .allergyStatus(AllergyStatus.CONFIRMED_ALLERGIES)
                .allergies(List.of(allergy))
                .pastMedicalHistory(List.of("Hypertension", "Type 2 Diabetes"))
                .chronicConditions(List.of("Asthma"))
                .build();

        when(profileRepository.save(any(PatientClinicalProfile.class))).thenAnswer(i -> i.getArgument(0));

        PatientClinicalProfile result = clinicalService.updatePatientProfile("patient-rec-1", req);

        assertThat(result.getAllergyStatus()).isEqualTo(AllergyStatus.CONFIRMED_ALLERGIES);
        assertThat(result.getAllergies()).hasSize(1);
        assertThat(result.getPastMedicalHistory()).contains("Hypertension");
        verify(auditService).audit(eq("patient-rec-1"), eq("PROFILE"), any(),
                eq(ClinicalAuditAction.CLINICAL_RECORD_UPDATED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("B2: Patient is denied from updating clinician-verified clinical profile")
    void testPatientCannotModifyClinicalProfile() {
        when(patientRepository.findById("patient-rec-1")).thenReturn(Optional.of(patient));
        when(authz.canModifyClinicalProfile("patient-rec-1")).thenReturn(false);

        UpdateClinicalProfileRequest req = UpdateClinicalProfileRequest.builder()
                .allergyStatus(AllergyStatus.NO_KNOWN_ALLERGIES)
                .build();

        assertThrows(AccessDeniedException.class, () -> clinicalService.updatePatientProfile("patient-rec-1", req));
    }

    @Test
    @DisplayName("B3: Empty clinical profile returns AllergyStatus.UNKNOWN")
    void testEmptyClinicalProfileReturnsUnknownAllergies() {
        when(patientRepository.findById("patient-rec-1")).thenReturn(Optional.of(patient));
        when(profileRepository.findByPatientId("patient-rec-1")).thenReturn(Optional.empty());

        PatientClinicalProfile profile = clinicalService.getPatientProfile("patient-rec-1");

        assertThat(profile.getAllergyStatus()).isEqualTo(AllergyStatus.UNKNOWN);
        assertThat(profile.getAllergies()).isEmpty();
    }

    // ==========================================
    // GROUP C: VITAL SIGNS
    // ==========================================

    @Test
    @DisplayName("C1: Clinician records valid vital signs within biological ranges")
    void testRecordValidVitals() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter enc = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("patient-rec-1")
                .build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(enc));

        RecordVitalsRequest req = RecordVitalsRequest.builder()
                .encounterId("enc-1")
                .systolicBp(120)
                .diastolicBp(80)
                .heartRate(72)
                .temperature(36.8)
                .respiratoryRate(16)
                .oxygenSaturation(98)
                .weightKg(70.5)
                .heightCm(175.0)
                .painScore(2)
                .clinicianNotes("Patient calm, resting vitals")
                .build();

        when(observationRepository.save(any(ClinicalObservation.class))).thenAnswer(i -> {
            ClinicalObservation obs = i.getArgument(0);
            obs.setId("obs-1");
            return obs;
        });

        ClinicalObservation saved = clinicalService.recordVitals(req);

        assertThat(saved.getId()).isEqualTo("obs-1");
        assertThat(saved.getHeartRate()).isEqualTo(72);
        assertThat(saved.getOxygenSaturation()).isEqualTo(98);
        verify(auditService).audit(eq("patient-rec-1"), eq("VITALS"), eq("obs-1"),
                eq(ClinicalAuditAction.CLINICAL_RECORD_CREATED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("C2: Rejects invalid vital signs exceeding plausible biological boundaries")
    void testRejectsInvalidVitalSigns() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter enc = ClinicalEncounter.builder().id("enc-1").patientId("patient-rec-1").build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(enc));

        // Impossible heart rate (500 bpm)
        RecordVitalsRequest invalidHr = RecordVitalsRequest.builder()
                .encounterId("enc-1")
                .heartRate(500)
                .build();
        assertThrows(InvalidVitalObservationException.class, () -> clinicalService.recordVitals(invalidHr));

        // Diastolic >= Systolic
        RecordVitalsRequest invalidBp = RecordVitalsRequest.builder()
                .encounterId("enc-1")
                .systolicBp(80)
                .diastolicBp(120)
                .build();
        assertThrows(InvalidVitalObservationException.class, () -> clinicalService.recordVitals(invalidBp));

        // Impossible SpO2 (150%)
        RecordVitalsRequest invalidSpo2 = RecordVitalsRequest.builder()
                .encounterId("enc-1")
                .oxygenSaturation(150)
                .build();
        assertThrows(InvalidVitalObservationException.class, () -> clinicalService.recordVitals(invalidSpo2));
    }

    // ==========================================
    // GROUP D: SOAP NOTES & SIGNING LIFECYCLE
    // ==========================================

    @Test
    @DisplayName("D1: Doctor edits SOAP note draft and saves draft")
    void testSaveSoapNoteDraft() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter enc = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.IN_PROGRESS)
                .version(1L)
                .build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(enc));

        ClinicalNoteRevision draft = ClinicalNoteRevision.builder()
                .id("note-1")
                .encounterId("enc-1")
                .revisionNumber(1)
                .status(NoteStatus.DRAFT)
                .version(1L)
                .build();
        when(noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc("enc-1")).thenReturn(Optional.of(draft));

        SaveSoapNoteDraftRequest req = SaveSoapNoteDraftRequest.builder()
                .subjective("Patient reports palpitations for 3 days.")
                .objective("BP 120/80, regular rhythm.")
                .assessment("Sinus tachycardia, likely stress-induced.")
                .diagnosisText("Sinus Tachycardia")
                .diagnosisStatus(DiagnosisStatus.PROVISIONAL)
                .plan("Rest, hydration, ECG, follow up in 1 week.")
                .expectedVersion(1L)
                .build();

        EncounterResponseDTO response = clinicalService.saveSoapNoteDraft("enc-1", req);

        assertThat(response).isNotNull();
        verify(noteRevisionRepository).save(any(ClinicalNoteRevision.class));
        verify(auditService).audit(eq("patient-rec-1"), eq("NOTE"), any(),
                eq(ClinicalAuditAction.CLINICAL_RECORD_UPDATED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("D2: Doctor signs note; encounter transitions to SIGNED and becomes immutable")
    void testDoctorSignsNoteSuccessfully() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter enc = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.IN_PROGRESS)
                .version(1L)
                .build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(enc));

        ClinicalNoteRevision draft = ClinicalNoteRevision.builder()
                .id("note-1")
                .encounterId("enc-1")
                .revisionNumber(1)
                .status(NoteStatus.DRAFT)
                .build();
        when(noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc("enc-1")).thenReturn(Optional.of(draft));
        when(encounterRepository.save(any(ClinicalEncounter.class))).thenAnswer(i -> i.getArgument(0));

        SignEncounterRequest signReq = SignEncounterRequest.builder()
                .expectedEncounterVersion(1L)
                .build();

        EncounterResponseDTO response = clinicalService.signEncounter("enc-1", signReq);

        assertThat(response.getStatus()).isEqualTo(EncounterStatus.SIGNED);
        verify(noteRevisionRepository).save(argThat(n -> n.getStatus() == NoteStatus.SIGNED));
        verify(outboxRepository).save(any(NotificationOutbox.class));
        verify(auditService).audit(eq("patient-rec-1"), eq("ENCOUNTER"), eq("enc-1"),
                eq(ClinicalAuditAction.CLINICAL_RECORD_SIGNED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("D3: Signed encounter cannot be modified directly via draft update")
    void testSignedEncounterDraftUpdateRejected() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter signed = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.SIGNED)
                .version(2L)
                .build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(signed));

        UpdateEncounterDraftRequest req = UpdateEncounterDraftRequest.builder()
                .chiefComplaint("New complaint")
                .build();

        assertThrows(ClinicalRecordConflictException.class, () -> clinicalService.updateEncounterDraft("enc-1", req));
    }

    @Test
    @DisplayName("D4: Doctor creates amendment on signed encounter preserving original revision")
    void testDoctorAmendsSignedEncounter() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        ClinicalEncounter signed = ClinicalEncounter.builder()
                .id("enc-1")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.SIGNED)
                .version(2L)
                .build();
        when(encounterRepository.findById("enc-1")).thenReturn(Optional.of(signed));

        ClinicalNoteRevision rev1 = ClinicalNoteRevision.builder()
                .id("note-rev-1")
                .encounterId("enc-1")
                .revisionNumber(1)
                .status(NoteStatus.SIGNED)
                .diagnosisText("Initial diagnosis")
                .build();
        when(noteRevisionRepository.findTopByEncounterIdOrderByRevisionNumberDesc("enc-1")).thenReturn(Optional.of(rev1));
        when(encounterRepository.save(any(ClinicalEncounter.class))).thenAnswer(i -> i.getArgument(0));

        AmendEncounterRequest amendReq = AmendEncounterRequest.builder()
                .amendmentReason("Added lab findings received after initial discharge")
                .expectedEncounterVersion(2L)
                .noteData(SaveSoapNoteDraftRequest.builder()
                        .assessment("Confirmed condition with secondary observation")
                        .diagnosisText("Confirmed Condition")
                        .build())
                .build();

        EncounterResponseDTO amended = clinicalService.amendEncounter("enc-1", amendReq);

        assertThat(amended.getStatus()).isEqualTo(EncounterStatus.AMENDED);
        // Verify new note revision saved with rev 2
        verify(noteRevisionRepository).save(argThat(n ->
                n.getRevisionNumber() == 2 &&
                n.getStatus() == NoteStatus.AMENDED &&
                n.getAmendmentReason().contains("Added lab findings")));
        verify(auditService).audit(eq("patient-rec-1"), eq("ENCOUNTER"), eq("enc-1"),
                eq(ClinicalAuditAction.CLINICAL_RECORD_AMENDED), eq("SUCCESS"), any(), any());
    }

    // ==========================================
    // GROUP E: CLINICAL TIMELINE
    // ==========================================

    @Test
    @DisplayName("E1: Patient viewing clinical timeline sees only finalized records, drafts filtered out")
    void testPatientTimelineFiltersOutDraftEncounters() {
        when(patientRepository.findById("patient-rec-1")).thenReturn(Optional.of(patient));
        when(authz.getCurrentUser()).thenReturn(patientUser);

        when(patientService.getMedicalHistory("patient-rec-1")).thenReturn(Collections.emptyList());

        ClinicalEncounter draftEnc = ClinicalEncounter.builder()
                .id("enc-draft")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.IN_PROGRESS)
                .visitStartedAt(Instant.now())
                .build();

        ClinicalEncounter signedEnc = ClinicalEncounter.builder()
                .id("enc-signed")
                .patientId("patient-rec-1")
                .doctorId("doctor-rec-1")
                .status(EncounterStatus.SIGNED)
                .visitStartedAt(Instant.now().minusSeconds(3600))
                .signedAt(Instant.now().minusSeconds(1800))
                .signedByDoctorName("Dr. Smith")
                .build();

        when(encounterRepository.findByPatientIdOrderByVisitStartedAtDesc("patient-rec-1"))
                .thenReturn(List.of(draftEnc, signedEnc));

        List<MedicalRecordDTO> timeline = clinicalService.getClinicalTimeline("patient-rec-1");

        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getId()).isEqualTo("enc-signed");
        assertThat(timeline.get(0).getStatus()).isEqualTo("SIGNED");
    }
}
