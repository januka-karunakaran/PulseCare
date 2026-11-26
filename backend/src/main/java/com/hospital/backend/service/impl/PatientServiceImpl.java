package com.hospital.backend.service.impl;

import com.hospital.backend.dto.MedicalRecordDTO;
import com.hospital.backend.dto.PatientRequest;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.Prescription;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.PrescriptionRepository;
import com.hospital.backend.repository.LabResultRepository;
import com.hospital.backend.repository.PharmacyDispenseRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final LabResultRepository labResultRepository;
    private final PharmacyDispenseRepository pharmacyDispenseRepository;
    private final UserRepository userRepository;

    @Override
    public Patient createPatient(PatientRequest request) {
        Instant now = Instant.now();
        Patient patient = Patient.builder()
            .userId(request.getUserId())
            .fullName(request.getFullName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .age(request.getAge())
            .gender(request.getGender())
            .bloodGroup(request.getBloodGroup())
            .address(request.getAddress())
            .createdAt(now)
            .updatedAt(now)
            .build();

        return patientRepository.save(patient);
    }

    @Override
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Override
    public Patient getPatientById(String id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new com.hospital.backend.exception.ResourceNotFoundException("Patient not found: " + id));
    }

    @Override
    public Patient updatePatient(String id, PatientRequest request) {
        Patient patient = getPatientById(id);

        patient.setFullName(request.getFullName());
        patient.setEmail(request.getEmail());
        patient.setPhone(request.getPhone());
        patient.setAge(request.getAge());
        patient.setGender(request.getGender());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setAddress(request.getAddress());
        patient.setUpdatedAt(Instant.now());

        return patientRepository.save(patient);
    }

    @Override
    public void deletePatient(String id) {
        patientRepository.deleteById(id);
    }

    @Override
    public Patient getMyProfile(String email) {
        return patientRepository.findByEmail(email)
                .or(() -> userRepository.findByEmail(email).flatMap(u -> patientRepository.findById(u.getId())))
                .orElseThrow(() -> new com.hospital.backend.exception.ResourceNotFoundException("Patient profile not found for email: " + email));
    }

    @Override
    public List<MedicalRecordDTO> getMedicalHistory(String patientId) {
        List<MedicalRecordDTO> timeline = new ArrayList<>();

        // Add Appointments
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        for (Appointment appt : appointments) {
            User doctor = userRepository.findById(appt.getDoctorId()).orElse(null);
            timeline.add(MedicalRecordDTO.builder()
                    .id(appt.getId())
                    .date(appt.getAppointmentDate())
                    .type("APPOINTMENT")
                    .title("Appointment with " + (doctor != null ? doctor.getFullName() : "Unknown"))
                    .description(appt.getReason())
                    .doctorName(doctor != null ? doctor.getFullName() : "Unknown")
                    .status(appt.getStatus())
                    .build());
        }

        // Add Prescriptions
        List<Prescription> prescriptions = prescriptionRepository.findByPatientId(patientId);
        for (Prescription presc : prescriptions) {
            User doctor = userRepository.findById(presc.getDoctorId()).orElse(null);
            String prescDate = "Unknown";
            if (presc.getCreatedAt() != null) {
                prescDate = presc.getCreatedAt().contains("T") ? presc.getCreatedAt().split("T")[0] : presc.getCreatedAt();
            }
            timeline.add(MedicalRecordDTO.builder()
                    .id(presc.getId())
                    .date(prescDate)
                    .type("PRESCRIPTION")
                    .title("Prescription from " + (doctor != null ? doctor.getFullName() : "Unknown"))
                    .description(presc.getDiagnosis() != null ? presc.getDiagnosis() : "Routine Checkup")
                    .doctorName(doctor != null ? doctor.getFullName() : "Unknown")
                    .status("ISSUED")
                    .build());
        }

        // Add Lab Results (Strict Clinical Verification: ONLY officially RELEASED results appear in patient medical record)
        List<com.hospital.backend.model.LabResult> labs = labResultRepository.findByPatientId(patientId);
        for (com.hospital.backend.model.LabResult lab : labs) {
            if (lab.getWorkflowStatus() != com.hospital.backend.enums.LabResultWorkflowStatus.RELEASED) {
                continue; // Do not leak DRAFT, ENTERED, VERIFIED (unreleased) or unverified legacy results to patient history
            }
            User doctor = lab.getDoctorId() != null ? userRepository.findById(lab.getDoctorId()).orElse(null) : null;
            String labDate = lab.getTestDate();
            if (labDate == null && lab.getReleasedAt() != null) {
                labDate = lab.getReleasedAt().toString().substring(0, 10);
            } else if (labDate == null && lab.getCreatedAt() != null) {
                labDate = lab.getCreatedAt().toString().substring(0, 10);
            }
            String displayResult = lab.getResultValue() != null ? lab.getResultValue() :
                    (lab.getNumericValue() != null ? String.valueOf(lab.getNumericValue()) : (lab.getTextValue() != null ? lab.getTextValue() : "Completed"));
            timeline.add(MedicalRecordDTO.builder()
                    .id(lab.getId())
                    .date(labDate != null ? labDate : "Unknown")
                    .type("LAB_RESULT")
                    .title(lab.getTestName())
                    .description("Result: " + displayResult + " " + (lab.getUnit() != null ? lab.getUnit() : ""))
                    .doctorName(doctor != null ? doctor.getFullName() : "Laboratory")
                    .status(lab.getStatus() != null ? lab.getStatus() : (lab.getInterpretation() != null ? lab.getInterpretation().name() : "NORMAL"))
                    .build());
        }

        // Add Pharmacy Dispenses (ONLY DISPENSED or PARTIALLY_DISPENSED)
        if (pharmacyDispenseRepository != null) {
            List<com.hospital.backend.model.PharmacyDispense> dispenses = pharmacyDispenseRepository.findByPatientIdOrderByDispensedAtDesc(patientId);
            if (dispenses != null) {
                for (com.hospital.backend.model.PharmacyDispense disp : dispenses) {
                    if (disp.getStatus() == com.hospital.backend.enums.DispenseStatus.CANCELLED ||
                        disp.getStatus() == com.hospital.backend.enums.DispenseStatus.REVERSED) {
                        continue;
                    }
                    String dispDate = disp.getDispensedAt() != null ? disp.getDispensedAt().toString().substring(0, 10) :
                            (disp.getCreatedAt() != null ? disp.getCreatedAt().toString().substring(0, 10) : "Unknown");
                    StringBuilder itemSummary = new StringBuilder();
                    if (disp.getItems() != null) {
                        for (com.hospital.backend.model.PharmacyDispense.DispenseLineItem item : disp.getItems()) {
                            if (itemSummary.length() > 0) itemSummary.append(", ");
                            itemSummary.append(item.getMedicineName() != null ? item.getMedicineName() : "Medicine")
                                    .append(" (").append(item.getDispensedQuantity()).append(" ").append(item.getDispenseUnit() != null ? item.getDispenseUnit() : "units").append(")");
                        }
                    }
                    timeline.add(MedicalRecordDTO.builder()
                            .id(disp.getId())
                            .date(dispDate)
                            .type("MEDICATION_DISPENSED")
                            .title("Dispensed Medication (" + disp.getDispenseNumber() + ")")
                            .description(itemSummary.length() > 0 ? itemSummary.toString() : "Medications dispensed")
                            .doctorName(disp.getPharmacistName() != null ? disp.getPharmacistName() : "Hospital Pharmacy")
                            .status(disp.getStatus() != null ? disp.getStatus().name() : "DISPENSED")
                            .build());
                }
            }
        }

        // Sort by date descending
        return timeline.stream()
                .sorted(Comparator.comparing(MedicalRecordDTO::getDate).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Patient addFamilyMember(String patientId, Patient.FamilyMember member) {
        Patient patient = getPatientById(patientId);
        List<Patient.FamilyMember> members = patient.getFamilyMembers();
        if (members == null) {
            members = new ArrayList<>();
        }
        member.setId(java.util.UUID.randomUUID().toString());
        members.add(member);
        patient.setFamilyMembers(members);
        patient.setUpdatedAt(Instant.now());
        return patientRepository.save(patient);
    }

    @Override
    public List<Patient.FamilyMember> getFamilyMembers(String patientId) {
        Patient patient = getPatientById(patientId);
        return patient.getFamilyMembers() != null ? patient.getFamilyMembers() : new ArrayList<>();
    }
}