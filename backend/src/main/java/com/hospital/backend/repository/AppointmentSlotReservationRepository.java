package com.hospital.backend.repository;

import com.hospital.backend.model.AppointmentSlotReservation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AppointmentSlotReservationRepository extends MongoRepository<AppointmentSlotReservation, String> {

    Optional<AppointmentSlotReservation> findByDoctorIdAndAppointmentDateAndAppointmentTime(
            String doctorId, String appointmentDate, String appointmentTime);

    java.util.List<AppointmentSlotReservation> findByDoctorIdAndAppointmentDate(
            String doctorId, String appointmentDate);

    Optional<AppointmentSlotReservation> findByAppointmentId(String appointmentId);

    void deleteByDoctorIdAndAppointmentDateAndAppointmentTimeAndAppointmentId(
            String doctorId, String appointmentDate, String appointmentTime, String appointmentId);

    void deleteByAppointmentId(String appointmentId);
}
