package com.hospital.backend.service;

import com.hospital.backend.dto.MedicalRecordDTO;
import com.hospital.backend.enums.LabResultWorkflowStatus;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.Prescription;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.LabResultRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.PrescriptionRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.impl.PatientServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientMedicalHistoryTest {

    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private com.hospital.backend.repository.PharmacyDispenseRepository pharmacyDispenseRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PatientServiceImpl patientService;

    @Test
    @DisplayName("Patient history: Only RELEASED lab results appear; unverified and legacy null status excluded")
    void getMedicalHistory_FiltersUnreleasedAndLegacyNullStatus() {
        String patientId = "pat-123";

        when(appointmentRepository.findByPatientId(patientId)).thenReturn(Collections.emptyList());
        when(prescriptionRepository.findByPatientId(patientId)).thenReturn(Collections.emptyList());

        LabResult released = LabResult.builder()
                .id("res-released")
                .testName("Complete Blood Count")
                .resultValue("Normal")
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .releasedAt(Instant.now())
                .build();

        LabResult draft = LabResult.builder()
                .id("res-draft")
                .testName("Fasting Blood Glucose")
                .resultValue("125")
                .workflowStatus(LabResultWorkflowStatus.DRAFT)
                .build();

        LabResult entered = LabResult.builder()
                .id("res-entered")
                .testName("Lipid Panel")
                .resultValue("Borderline")
                .workflowStatus(LabResultWorkflowStatus.ENTERED)
                .build();

        LabResult verified = LabResult.builder()
                .id("res-verified")
                .testName("Thyroid Profile")
                .resultValue("Normal")
                .workflowStatus(LabResultWorkflowStatus.VERIFIED)
                .build();

        LabResult legacyNull = LabResult.builder()
                .id("res-legacy-null")
                .testName("Legacy Blood Test")
                .resultValue("14.0")
                .workflowStatus(null) // Unverified legacy record
                .build();

        when(labResultRepository.findByPatientId(patientId))
                .thenReturn(List.of(released, draft, entered, verified, legacyNull));

        List<MedicalRecordDTO> history = patientService.getMedicalHistory(patientId);

        // Only the RELEASED result should appear in the patient timeline
        assertEquals(1, history.size());
        assertEquals("res-released", history.get(0).getId());
        assertEquals("LAB_RESULT", history.get(0).getType());
        assertEquals("Complete Blood Count", history.get(0).getTitle());
    }
}
