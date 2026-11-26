package com.hospital.backend.repository;

import com.hospital.backend.model.BedOccupancyEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BedOccupancyEventRepository extends MongoRepository<BedOccupancyEvent, String> {

    List<BedOccupancyEvent> findByAdmissionIdOrderBySequenceNumberAsc(String admissionId);

    List<BedOccupancyEvent> findByBedIdOrderByOccurredAtAsc(String bedId);

    List<BedOccupancyEvent> findByBedIdAndOccurredAtBetweenOrderByOccurredAtAsc(String bedId, Instant start, Instant end);

    List<BedOccupancyEvent> findByPatientIdOrderByOccurredAtDesc(String patientId);

    List<BedOccupancyEvent> findByOccurredAtBetweenOrderByOccurredAtAsc(Instant start, Instant end);

    Optional<BedOccupancyEvent> findByEventId(String eventId);

    Optional<BedOccupancyEvent> findByIdempotencyKey(String idempotencyKey);

    long countByAdmissionId(String admissionId);
}
