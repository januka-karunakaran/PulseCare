package com.hospital.backend.mapper;

import com.hospital.backend.dto.PatientRequest;
import com.hospital.backend.model.Patient;
import org.springframework.stereotype.Component;

/**
 * Enterprise Mapper for Patient entities and DTOs.
 */
@Component
public class PatientMapper {

    public Patient toEntity(PatientRequest request) {
        if (request == null) return null;

        return Patient.builder()
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .age(request.getAge())
                .gender(request.getGender())
                .bloodGroup(request.getBloodGroup())
                .address(request.getAddress())
                .build();
    }

    public void updateEntityFromRequest(Patient patient, PatientRequest request) {
        if (patient == null || request == null) return;

        patient.setFullName(request.getFullName());
        patient.setEmail(request.getEmail());
        patient.setPhone(request.getPhone());
        patient.setAge(request.getAge());
        patient.setGender(request.getGender());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setAddress(request.getAddress());
    }
}
