package com.hospital.backend.service;

import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Doctor;
import com.hospital.backend.model.Patient;
import com.hospital.backend.repository.*;
import com.hospital.backend.service.impl.AppointmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
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

    @Test
    public void getAppointmentsByPatientId_WhenUserIdDiffersFromPatientProfileId_ReturnsBothFormats() {
        String profileId = "profile-patient-999";
        String userId = "user-account-456";

        Patient patient = Patient.builder()
                .id(profileId)
                .userId(userId)
                .fullName("John Doe")
                .build();

        Appointment apptByProfileId = Appointment.builder()
                .id("appt-legacy-1")
                .patientId(profileId)
                .doctorId("doc-1")
                .appointmentDate("2026-06-01")
                .appointmentTime("09:00 AM")
                .status("CONFIRMED")
                .build();

        Appointment apptByUserId = Appointment.builder()
                .id("appt-modern-2")
                .patientId(userId)
                .doctorId("doc-1")
                .appointmentDate("2026-06-10")
                .appointmentTime("11:00 AM")
                .status("PENDING")
                .build();

        when(patientRepository.findById(userId)).thenReturn(Optional.empty());
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findByPatientIdIn(anyList())).thenReturn(List.of(apptByProfileId, apptByUserId));

        List<Appointment> results = appointmentService.getAppointmentsByPatientId(userId);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(a -> profileId.equals(a.getPatientId())));
        assertTrue(results.stream().anyMatch(a -> userId.equals(a.getPatientId())));
    }

    @Test
    public void getAppointmentsByDoctorId_WhenDoctorProfileIdDiffersFromUserId_ResolvesSchedule() {
        String docProfileId = "doc-profile-777";
        String docUserId = "doc-user-888";

        Doctor doctor = Doctor.builder()
                .id(docProfileId)
                .userId(docUserId)
                .fullName("Dr. Gregory House")
                .build();

        Appointment docAppt = Appointment.builder()
                .id("appt-doc-1")
                .patientId("patient-1")
                .doctorId(docProfileId)
                .appointmentDate("2026-06-05")
                .appointmentTime("02:00 PM")
                .status("CONFIRMED")
                .build();

        when(doctorRepository.findById(docUserId)).thenReturn(Optional.empty());
        when(doctorRepository.findByUserId(docUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorIdIn(anyList())).thenReturn(List.of(docAppt));

        List<Appointment> results = appointmentService.getAppointmentsByDoctorId(docUserId);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("appt-doc-1", results.get(0).getId());
    }
}
