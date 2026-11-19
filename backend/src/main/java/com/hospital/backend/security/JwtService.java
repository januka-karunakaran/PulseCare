package com.hospital.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private long jwtExpiration;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @jakarta.annotation.PostConstruct
    public void validateSecretKey() {
        if (activeProfile != null && activeProfile.contains("prod")) {
            if (secretKey == null || secretKey.isBlank() || secretKey.length() < 32
                    || secretKey.contains("change-me")
                    || secretKey.contains("change-this-in-production")
                    || "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".equals(secretKey)) {
                throw new IllegalStateException("FATAL: In production profile, a unique and secure JWT_SECRET of at least 256 bits (32 characters) must be configured.");
            }
        }
    }

    public String generateToken(UserDetails userDetails, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        long nowMs = System.currentTimeMillis();
        claims.put("iat_ms", nowMs);

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    public Long extractIssuedAtMs(String token) {
        Claims claims = extractAllClaims(token);
        Object iatMsObj = claims.get("iat_ms");
        if (iatMsObj instanceof Number num) {
            return num.longValue();
        }
        Date iat = claims.getIssuedAt();
        return iat != null ? iat.getTime() : null;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }

        if (userDetails instanceof com.hospital.backend.model.User user) {
            if (user.getTokenInvalidBefore() != null) {
                Long issuedAtMs = extractIssuedAtMs(token);
                if (issuedAtMs != null) {
                    long invalidBeforeMs = user.getTokenInvalidBefore().toEpochMilli();
                    if (issuedAtMs < invalidBeforeMs) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        final Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}