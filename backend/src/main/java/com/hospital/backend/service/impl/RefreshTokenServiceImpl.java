package com.hospital.backend.service.impl;

import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.repository.RefreshTokenRepository;
import com.hospital.backend.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final MongoTemplate mongoTemplate;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final long REFRESH_TOKEN_DURATION = 7L * 24 * 60 * 60; // 7 days

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
            mongoTemplate.remove(refreshToken);
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

        // Database-enforced atomic consume operation across all application instances
        Query query = Query.query(Criteria.where("token").is(hashedToken));
        RefreshToken consumedToken = mongoTemplate.findAndRemove(query, RefreshToken.class);

        if (consumedToken == null) {
            // Fallback for unhashed tokens (legacy support or tests)
            consumedToken = mongoTemplate.findAndRemove(Query.query(Criteria.where("token").is(token)), RefreshToken.class);
        }

        if (consumedToken == null) {
            throw new BadRequestException("Invalid or revoked refresh token");
        }

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
    }

    @Override
    public void deleteByUserId(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) return;
        String hashedToken = hashToken(token);
        mongoTemplate.remove(Query.query(Criteria.where("token").in(hashedToken, token)), RefreshToken.class);
    }
}
