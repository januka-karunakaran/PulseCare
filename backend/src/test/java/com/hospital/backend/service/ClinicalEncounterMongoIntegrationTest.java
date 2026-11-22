package com.hospital.backend.service;

import com.hospital.backend.enums.*;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class ClinicalEncounterMongoIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.4");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired private ClinicalEncounterRepository encounterRepository;
    @Autowired private ClinicalNoteRevisionRepository noteRevisionRepository;
    @Autowired private ClinicalObservationRepository observationRepository;
    @Autowired private PatientClinicalProfileRepository profileRepository;
    @Autowired private ClinicalAuditLogRepository auditLogRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        encounterRepository.deleteAll();
        noteRevisionRepository.deleteAll();
        observationRepository.deleteAll();
        profileRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("MongoDB saves and retrieves ClinicalEncounter with versioning")
    void testSaveAndRetrieveEncounter() {
        Instant now = Instant.now();
        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .appointmentId("appt-mongo-1")
                .patientId("pat-mongo-1")
                .doctorId("doc-mongo-1")
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.IN_PROGRESS)
                .chiefComplaint("Persistent cough")
                .visitStartedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ClinicalEncounter saved = encounterRepository.save(encounter);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();

        Optional<ClinicalEncounter> fetched = encounterRepository.findById(saved.getId());
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getChiefComplaint()).isEqualTo("Persistent cough");
    }

    @Test
    @DisplayName("Optimistic locking on ClinicalEncounter throws OptimisticLockingFailureException on stale version write")
    void testOptimisticLockingOnEncounter() {
        ClinicalEncounter encounter = ClinicalEncounter.builder()
                .appointmentId("appt-concurrency-1")
                .patientId("pat-1")
                .doctorId("doc-1")
                .status(EncounterStatus.IN_PROGRESS)
                .build();

        ClinicalEncounter saved = encounterRepository.save(encounter);

        // Session A loads entity
        ClinicalEncounter sessionA = encounterRepository.findById(saved.getId()).orElseThrow();
        // Session B loads entity
        ClinicalEncounter sessionB = encounterRepository.findById(saved.getId()).orElseThrow();

        // Session A updates and saves first
        sessionA.setChiefComplaint("Updated by Session A");
        encounterRepository.save(sessionA);

        // Session B tries to update with stale version
        sessionB.setChiefComplaint("Conflicting update by Session B");
        assertThrows(OptimisticLockingFailureException.class, () -> encounterRepository.save(sessionB));
    }

    @Test
    @DisplayName("SOAP Note revisions increment revisionNumber and preserve amendment history")
    void testNoteRevisionHistoryPreserved() {
        Instant now = Instant.now();
        String encounterId = "enc-hist-1";

        ClinicalNoteRevision rev1 = ClinicalNoteRevision.builder()
                .encounterId(encounterId)
                .patientId("pat-1")
                .doctorId("doc-1")
                .revisionNumber(1)
                .status(NoteStatus.SIGNED)
                .diagnosisText("Bronchitis")
                .signedAt(now)
                .build();
        noteRevisionRepository.save(rev1);

        ClinicalNoteRevision rev2 = ClinicalNoteRevision.builder()
                .encounterId(encounterId)
                .patientId("pat-1")
                .doctorId("doc-1")
                .revisionNumber(2)
                .status(NoteStatus.AMENDED)
                .amendmentReason("Chest X-ray confirms mild pneumonia")
                .diagnosisText("Pneumonia")
                .signedAt(now.plusSeconds(300))
                .build();
        noteRevisionRepository.save(rev2);

        List<ClinicalNoteRevision> revisions = noteRevisionRepository.findByEncounterIdOrderByRevisionNumberAsc(encounterId);
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(1);
        assertThat(revisions.get(0).getStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(revisions.get(1).getRevisionNumber()).isEqualTo(2);
        assertThat(revisions.get(1).getStatus()).isEqualTo(NoteStatus.AMENDED);
        assertThat(revisions.get(1).getAmendmentReason()).contains("Chest X-ray");
    }

    @Test
    @DisplayName("ClinicalObservations persists vitals with timestamp and patient index")
    void testSaveAndQueryObservations() {
        ClinicalObservation obs1 = ClinicalObservation.builder()
                .encounterId("enc-obs-1")
                .patientId("pat-obs-1")
                .observedAt(Instant.now().minusSeconds(60))
                .systolicBp(120)
                .diastolicBp(80)
                .heartRate(72)
                .temperature(37.0)
                .oxygenSaturation(98)
                .build();

        ClinicalObservation obs2 = ClinicalObservation.builder()
                .encounterId("enc-obs-1")
                .patientId("pat-obs-1")
                .observedAt(Instant.now())
                .systolicBp(125)
                .diastolicBp(82)
                .heartRate(75)
                .temperature(37.1)
                .oxygenSaturation(99)
                .build();

        observationRepository.saveAll(List.of(obs1, obs2));

        List<ClinicalObservation> list = observationRepository.findByPatientIdOrderByObservedAtDesc("pat-obs-1");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getSystolicBp()).isEqualTo(125);
    }
}
