package com.hospital.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductionEnvironmentValidatorTest {

    private ProductionEnvironmentValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new ProductionEnvironmentValidator();
        validator.setMongoUri("mongodb://hms_prod_user:StrongPassword123@prod-cluster.internal:27017/hospital_management_db?authSource=admin");
        validator.setJwtSecret("c4389b02e7df83cfa612809e537e29cb73b318c44e99f660ba26c9b5894b1509");
        validator.setCorsAllowedOrigins("https://hospital.example.com,https://admin.hospital.example.com");
    }

    @Test
    public void validProductionConfiguration_PassesValidation() {
        assertDoesNotThrow(() -> validator.validateConfiguration());
    }

    @Test
    public void missingMongoUri_ThrowsIllegalStateException() {
        validator.setMongoUri("");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("MONGODB_URI is required"));
    }

    @Test
    public void localhostMongoUri_InProduction_ThrowsIllegalStateException() {
        validator.setMongoUri("mongodb://localhost:27017/hospital_management_db");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("localhost/127.0.0.1"));
    }

    @Test
    public void defaultDevJwtSecret_ThrowsIllegalStateException() {
        validator.setJwtSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("known development default"));
    }

    @Test
    public void shortJwtSecret_ThrowsIllegalStateException() {
        validator.setJwtSecret("short-key-12345");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("insufficient"));
    }

    @Test
    public void repeatingLowEntropySecret_ThrowsIllegalStateException() {
        validator.setJwtSecret("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("cryptographic randomness"));
    }

    @Test
    public void wildcardCors_InProduction_ThrowsIllegalStateException() {
        validator.setCorsAllowedOrigins("*");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validateConfiguration());
        assertTrue(ex.getMessage().contains("Wildcard '*' in CORS_ALLOWED_ORIGINS is strictly prohibited"));
    }
}
