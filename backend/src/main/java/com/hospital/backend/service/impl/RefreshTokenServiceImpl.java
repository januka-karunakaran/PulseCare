package com.hospital.backend.service.impl;

import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.repository.RefreshTokenRepository;
import com.hospital.backend.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private static final long REFRESH_TOKEN_DURATION = 7L * 24 * 60 * 60; // 7 days

    // In-memory locks to serialize concurrent token consumption attempts per token within the JVM
    private final ConcurrentHashMap<String, Object> tokenLocks = new ConcurrentHashMap<>();

    public static String hashToken(String token) {
        if (token == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public RefreshToken createRefreshToken(String userId) {
        refreshTokenRepository.deleteByUserId(userId);

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .token(hashedToken)
                .expiryDate(Instant.now().plusSeconds(REFRESH_TOKEN_DURATION))
                .build();

        RefreshToken saved = refreshTokenRepository.save(token);

        // Return object containing the raw token so caller can transmit it to the client
        return RefreshToken.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .token(rawToken)
                .expiryDate(saved.getExpiryDate())
                .build();
    }

    @Override
    public RefreshToken verifyToken(String token) {
        String hashedToken = hashToken(token);
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashedToken)
                .or(() -> refreshTokenRepository.findByToken(token))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadRequestException("Refresh token expired");
        }

        return refreshToken;
    }

    @Override
    public RefreshToken rotateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Invalid or revoked refresh token");
        }

        String hashedToken = hashToken(token);
        Object lock = tokenLocks.computeIfAbsent(hashedToken, k -> new Object());

        synchronized (lock) {
            try {
                // Atomic consumption: find and delete the token in a single atomic database operation
                Optional<RefreshToken> deletedTokenOpt = refreshTokenRepository.deleteByToken(hashedToken);
                if (deletedTokenOpt.isEmpty()) {
                    // Fallback lookup in case token was saved unhashed (legacy support or tests)
                    deletedTokenOpt = refreshTokenRepository.deleteByToken(token);
                }

                if (deletedTokenOpt.isEmpty()) {
                    throw new BadRequestException("Invalid or revoked refresh token");
                }

                RefreshToken consumedToken = deletedTokenOpt.get();

                if (consumedToken.getExpiryDate().isBefore(Instant.now())) {
                    throw new BadRequestException("Refresh token expired");
                }

                String userId = consumedToken.getUserId();
                String newRawToken = UUID.randomUUID().toString();
                String newHashedToken = hashToken(newRawToken);

                RefreshToken newToken = RefreshToken.builder()
                        .userId(userId)
                        .token(newHashedToken)
                        .expiryDate(Instant.now().plusSeconds(REFRESH_TOKEN_DURATION))
                        .build();

                RefreshToken savedToken = refreshTokenRepository.save(newToken);

                return RefreshToken.builder()
                        .id(savedToken.getId())
                        .userId(savedToken.getUserId())
                        .token(newRawToken)
                        .expiryDate(savedToken.getExpiryDate())
                        .build();
            } finally {
                tokenLocks.remove(hashedToken);
            }
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) return;
        String hashedToken = hashToken(token);
        refreshTokenRepository.deleteByToken(hashedToken);
        refreshTokenRepository.deleteByToken(token);
    }
}
