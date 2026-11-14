package com.hospital.backend.service;

import com.hospital.backend.dto.AuthResponse;
import com.hospital.backend.dto.LoginRequest;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.RefreshToken;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.JwtService;
import com.hospital.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    public void setup() {
        sampleUser = User.builder()
                .id("u1")
                .fullName("John Doe")
                .email("john@example.com")
                .password("encodedPassword")
                .role(Role.PATIENT)
                .build();
    }

    @Test
    public void login_Success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("rawPassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(any(UserDetails.class), anyString())).thenReturn("mock-jwt-token");
        when(refreshTokenService.createRefreshToken("u1")).thenReturn(RefreshToken.builder().token("mock-refresh-token").build());

        AuthResponse response = authService.login(req);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("John Doe", response.getFullName());
    }

    @Test
    public void login_InvalidPassword_ThrowsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("wrongPassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.login(req));
    }
}
