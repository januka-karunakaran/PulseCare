package com.hospital.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    public void setup() {
        jwtService = new JwtService();
        // Base64-compatible secret key of sufficient length
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour
    }

    @Test
    public void generateAndValidateToken_Success() {
        UserDetails userDetails = new User("doctor@hospital.com", "password", Collections.emptyList());

        String token = jwtService.generateToken(userDetails, "ROLE_DOCTOR");
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String username = jwtService.extractUsername(token);
        assertEquals("doctor@hospital.com", username);

        String role = jwtService.extractRole(token);
        assertEquals("ROLE_DOCTOR", role);

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }
}
