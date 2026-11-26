package com.hospital.backend.service.impl;

import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.HospitalWard;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.repository.HospitalWardRepository;
import com.hospital.backend.service.WardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WardServiceImpl implements WardService {

    private final HospitalWardRepository wardRepository;
    private final BedRepository bedRepository;

    @Override
    public HospitalWard createWard(HospitalWard ward) {
        if (ward.getWardCode() == null || ward.getWardCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Ward code is mandatory");
        }
        String normalizedCode = ward.getWardCode().trim().toUpperCase();
        if (wardRepository.existsByWardCode(normalizedCode)) {
            throw new DuplicateResourceException("Ward with code " + normalizedCode + " already exists");
        }

        Instant now = Instant.now();
        ward.setWardCode(normalizedCode);
        ward.setCreatedAt(now);
        ward.setUpdatedAt(now);
        return wardRepository.save(ward);
    }

    @Override
    public HospitalWard updateWard(String id, HospitalWard update) {
        HospitalWard existing = getWardById(id);
        existing.setWardName(update.getWardName());
        existing.setWardType(update.getWardType());
        existing.setDescription(update.getDescription());
        existing.setActive(update.isActive());
        existing.setTotalBeds(update.getTotalBeds());
        existing.setUpdatedAt(Instant.now());
        return wardRepository.save(existing);
    }

    @Override
    public HospitalWard getWardById(String id) {
        return wardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital ward not found with ID: " + id));
    }

    @Override
    public HospitalWard getWardByCode(String wardCode) {
        if (wardCode == null) {
            throw new IllegalArgumentException("Ward code must not be null");
        }
        return wardRepository.findByWardCode(wardCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital ward not found with code: " + wardCode));
    }

    @Override
    public List<HospitalWard> getAllWards() {
        return wardRepository.findAll();
    }

    @Override
    public List<HospitalWard> getActiveWards() {
        return wardRepository.findByActive(true);
    }

    @Override
    public void deleteWard(String id) {
        HospitalWard ward = getWardById(id);
        long bedCount = bedRepository.findByWardId(id).size();
        if (bedCount == 0 && ward.getWardName() != null) {
            bedCount = bedRepository.findByWard(ward.getWardName()).size();
        }
        if (bedCount > 0) {
            throw new InvalidBookingTransitionException("Cannot delete ward containing " + bedCount + " registered beds. Deactivate the ward instead.");
        }
        wardRepository.delete(ward);
    }
}
