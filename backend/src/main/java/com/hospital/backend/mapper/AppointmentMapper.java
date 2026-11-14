package com.hospital.backend.mapper;

import com.hospital.backend.dto.AppointmentRequest;
import com.hospital.backend.model.Appointment;
import org.springframework.stereotype.Component;

/**
 * Enterprise Mapper for Appointment entities and DTOs.
 */
@Component
public class AppointmentMapper {

    public Appointment toEntity(AppointmentRequest request) {
        if (request == null) return null;

        return Appointment.builder()
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .reason(request.getReason())
                .status("PENDING")
                .build();
    }

    public void updateEntityFromRequest(Appointment appointment, AppointmentRequest request) {
        if (appointment == null || request == null) return;

        appointment.setPatientId(request.getPatientId());
        appointment.setDoctorId(request.getDoctorId());
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setAppointmentTime(request.getAppointmentTime());
        appointment.setReason(request.getReason());
    }
}
