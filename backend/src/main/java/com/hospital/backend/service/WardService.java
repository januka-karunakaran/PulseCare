package com.hospital.backend.service;

import com.hospital.backend.model.HospitalWard;

import java.util.List;

public interface WardService {
    HospitalWard createWard(HospitalWard ward);
    HospitalWard updateWard(String id, HospitalWard ward);
    HospitalWard getWardById(String id);
    HospitalWard getWardByCode(String wardCode);
    List<HospitalWard> getAllWards();
    List<HospitalWard> getActiveWards();
    void deleteWard(String id);
}
