package com.hospital.backend.service;

import com.hospital.backend.model.RefreshToken;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(String userId);
    RefreshToken verifyToken(String token);
    RefreshToken rotateRefreshToken(String token);
    void deleteByUserId(String userId);
    void deleteByToken(String token);
}
