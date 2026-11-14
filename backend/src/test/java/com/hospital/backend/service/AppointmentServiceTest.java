package com.hospital.backend.service;

import com.hospital.backend.model.Appointment;
import com.hospital.backend.repository.*;
import com.hospital.backend.service.impl.AppointmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    @Test
    public void getAppointmentsByPatientId_Success() {
        Appointment apt = Appointment.builder()
                .id("a1")
                .patientId("p1")
                .doctorId("d1")
                .appointmentDate("2026-05-15")
                .appointmentTime("10:00 AM")
                .status("CONFIRMED")
                .build();

        when(appointmentRepository.findByPatientId("p1")).thenReturn(List.of(apt));

        List<Appointment> result = appointmentService.getAppointmentsByPatientId("p1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).getId());
    }
}
