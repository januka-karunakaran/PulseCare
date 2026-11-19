package com.hospital.backend.security;

import com.hospital.backend.dto.SignupRequest;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.EmailVerificationCode;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.DoctorRepository;
import com.hospital.backend.repository.EmailVerificationCodeRepository;
import com.hospital.backend.repository.PasswordResetTokenRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.RefreshTokenService;
import com.hospital.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RegistrationSecurityTest {

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

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AuthServiceImpl authService;

    private EmailVerificationCode validOtp;

    @BeforeEach
    public void setup() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest("test@example.com:123456".getBytes(StandardCharsets.UTF_8));
        String codeHash = HexFormat.of().formatHex(hash);

        validOtp = EmailVerificationCode.builder()
                .email("test@example.com")
                .codeHash(codeHash)
                .failedAttempts(0)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    @Test
    public void publicSignup_RequestingAdminRole_ThrowsBadRequestException() {
        SignupRequest request = new SignupRequest();
        request.setFullName("Attacker");
        request.setEmail("test@example.com");
        request.setPassword("Password123!");
        request.setRole("ADMIN");
        request.setOtp("123456");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(emailVerificationCodeRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validOtp));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.signup(request));
        assertTrue(ex.getMessage().contains("Public registration only allows PATIENT role"));
        verify(userRepository, never()).save(any());
        verify(doctorRepository, never()).save(any());
    }

    @Test
    public void publicSignup_RequestingDoctorRole_ThrowsBadRequestException() {
        SignupRequest request = new SignupRequest();
        request.setFullName("Fake Doctor");
        request.setEmail("test@example.com");
        request.setPassword("Password123!");
        request.setRole("DOCTOR");
        request.setOtp("123456");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(emailVerificationCodeRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validOtp));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.signup(request));
        assertTrue(ex.getMessage().contains("Public registration only allows PATIENT role"));
        verify(userRepository, never()).save(any());
        verify(doctorRepository, never()).save(any());
    }

    @Test
    public void publicSignup_WithPatientRole_SucceedsAndCreatesPatientAccountOnly() {
        SignupRequest request = new SignupRequest();
        request.setFullName("Legitimate Patient");
        request.setEmail("test@example.com");
        request.setPassword("Password123!");
        request.setRole("PATIENT");
        request.setOtp("123456");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(emailVerificationCodeRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validOtp));
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");

        User savedUser = User.builder()
                .id("patient-1")
                .fullName("Legitimate Patient")
                .email("test@example.com")
                .role(Role.PATIENT)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        String result = authService.signup(request);

        assertEquals("Signup successful", result);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.PATIENT, userCaptor.getValue().getRole());

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        assertEquals("patient-1", patientCaptor.getValue().getUserId());

        verify(doctorRepository, never()).save(any());
    }

    @Test
    public void publicSignup_WithoutRoleField_DefaultsToPatient() {
        SignupRequest request = new SignupRequest();
        request.setFullName("No Role User");
        request.setEmail("test@example.com");
        request.setPassword("Password123!");
        request.setRole(null);
        request.setOtp("123456");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(emailVerificationCodeRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validOtp));
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");

        User savedUser = User.builder()
                .id("patient-2")
                .fullName("No Role User")
                .email("test@example.com")
                .role(Role.PATIENT)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        String result = authService.signup(request);
        assertEquals("Signup successful", result);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.PATIENT, userCaptor.getValue().getRole());
    }
}
