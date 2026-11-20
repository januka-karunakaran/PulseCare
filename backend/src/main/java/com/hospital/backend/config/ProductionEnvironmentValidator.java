package com.hospital.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Fail-Closed Production Environment Validator.
 * Validates critical infrastructure configuration and secret strength at application startup.
 * Halts startup immediately with an IllegalStateException if insecure or placeholder values are detected.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionEnvironmentValidator implements ApplicationRunner {

    private static final String DEFAULT_DEV_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int MINIMUM_JWT_SECRET_BYTES = 32; // 256 bits

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Override
    public void run(ApplicationArguments args) {
        validateConfiguration();
    }

    public void validateConfiguration() {
        log.info("Executing fail-closed production environment validation...");

        // 1. Validate MongoDB URI
        if (!StringUtils.hasText(mongoUri)) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: MONGODB_URI is required and cannot be empty in production.");
        }
        if (mongoUri.contains("localhost") || mongoUri.contains("127.0.0.1")) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: MONGODB_URI points to localhost/127.0.0.1 in production profile. A production-grade MongoDB URI is required.");
        }

        // 2. Validate JWT Secret
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: JWT_SECRET is required and cannot be empty in production.");
        }
        if (jwtSecret.equalsIgnoreCase(DEFAULT_DEV_JWT_SECRET) || jwtSecret.toLowerCase().contains("replace") || jwtSecret.toLowerCase().contains("secret")) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET is using a known development default or template placeholder. A unique 256-bit cryptographically secure secret must be configured.");
        }
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET strength insufficient (" + secretBytes.length + " bytes). Production requires at least 32 bytes (256 bits) of entropy.");
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
