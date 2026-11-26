package com.hospital.backend.repository;

import com.hospital.backend.model.BedBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BedBookingRepository extends MongoRepository<BedBooking, String> {

    List<BedBooking> findByPatientId(String patientId);

    List<BedBooking> findByPatientIdOrderByCreatedAtDesc(String patientId);

    List<BedBooking> findByPatientIdOrderByAdmittedAtDesc(String patientId);

    List<BedBooking> findByStatus(String status);

    List<BedBooking> findByStatusIn(List<String> statuses);

    Page<BedBooking> findByStatusIn(List<String> statuses, Pageable pageable);

    Optional<BedBooking> findByAdmissionNumber(String admissionNumber);

    Optional<BedBooking> findByIdempotencyKey(String idempotencyKey);

    Optional<BedBooking> findFirstByBedIdAndStatusIn(String bedId, List<String> statuses);

    Optional<BedBooking> findFirstByPatientIdAndStatusIn(String patientId, List<String> statuses);

    List<BedBooking> findByDoctorIdAndStatusIn(String doctorId, List<String> statuses);

    List<BedBooking> findByAdmittedAtBetween(Instant start, Instant end);

    List<BedBooking> findByDischargedAtBetween(Instant start, Instant end);
}
