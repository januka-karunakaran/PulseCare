package com.hospital.backend.repository;

import com.hospital.backend.enums.NoteStatus;
import com.hospital.backend.model.ClinicalNoteRevision;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalNoteRevisionRepository extends MongoRepository<ClinicalNoteRevision, String> {
    List<ClinicalNoteRevision> findByEncounterIdOrderByRevisionNumberAsc(String encounterId);
    Optional<ClinicalNoteRevision> findTopByEncounterIdOrderByRevisionNumberDesc(String encounterId);
    List<ClinicalNoteRevision> findByPatientIdAndStatusIn(String patientId, Collection<NoteStatus> statuses);
    List<ClinicalNoteRevision> findByPatientIdOrderByCreatedAtDesc(String patientId);
}
