package com.hospital.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Fail-Closed Production Environment Validator.
 * Implements InitializingBean to validate critical infrastructure configuration and secret strength
 * BEFORE the embedded HTTP server (Tomcat) binds to the network port.
 * Halts startup immediately with an IllegalStateException if insecure, placeholder, or low-entropy values are detected.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionEnvironmentValidator implements InitializingBean {

    private static final String DEFAULT_DEV_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int MINIMUM_JWT_SECRET_BYTES = 32; // 256 bits
    private static final double MINIMUM_SHANNON_ENTROPY = 3.0; // Enforces genuine cryptographic entropy

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Override
    public void afterPropertiesSet() {
        validateConfiguration();
    }

    public void validateConfiguration() {
        log.info("Executing fail-closed production environment validation before HTTP server binds...");

        // 1. Validate MongoDB URI
        if (!StringUtils.hasText(mongoUri)) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: MONGODB_URI is required and cannot be empty in production.");
        }
        if (mongoUri.contains("localhost") || mongoUri.contains("127.0.0.1")) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: MONGODB_URI points to localhost/127.0.0.1 in production profile. A production-grade MongoDB URI is required.");
        }

        // 2. Validate JWT Secret Presence and Known Placeholders
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: JWT_SECRET is required and cannot be empty in production.");
        }
        String lowerSecret = jwtSecret.toLowerCase();
        if (jwtSecret.equalsIgnoreCase(DEFAULT_DEV_JWT_SECRET)
                || lowerSecret.contains("replace")
                || lowerSecret.contains("secret")
                || lowerSecret.contains("password")
                || lowerSecret.contains("default")
                || lowerSecret.contains("admin")) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET uses a known development default, template placeholder, or predictable pattern. A unique 256-bit cryptographically secure secret must be configured.");
        }

        // Validate Key Byte Length
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET strength insufficient (" + secretBytes.length + " bytes). Production requires at least 32 bytes (256 bits) of entropy.");
        }

        // Validate Cryptographic Entropy
        double entropy = calculateShannonEntropy(jwtSecret);
        long distinctChars = jwtSecret.chars().distinct().count();
        if (distinctChars < 12 || entropy < MINIMUM_SHANNON_ENTROPY) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET lacks sufficient cryptographic randomness (Entropy: "
                    + String.format("%.2f", entropy) + ", distinct chars: " + distinctChars + "). Low-entropy or repeating keys are rejected.");
        }

        // 3. Validate CORS Allowed Origins
        if (!StringUtils.hasText(corsAllowedOrigins)) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: CORS_ALLOWED_ORIGINS is required and cannot be empty in production.");
        }
        if (corsAllowedOrigins.contains("*")) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: Wildcard '*' in CORS_ALLOWED_ORIGINS is strictly prohibited in production.");
        }

        log.info("Production environment configuration successfully validated with fail-closed safety guarantees.");
    }

    private static double calculateShannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        Map<Integer, Integer> frequencies = new HashMap<>();
        s.chars().forEach(c -> frequencies.put(c, frequencies.getOrDefault(c, 0) + 1));
        double entropy = 0.0;
        int len = s.length();
        for (int count : frequencies.values()) {
            double p = (double) count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    // Setters for direct unit testing without full Spring Boot context
    public void setMongoUri(String mongoUri) {
        this.mongoUri = mongoUri;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }
}
