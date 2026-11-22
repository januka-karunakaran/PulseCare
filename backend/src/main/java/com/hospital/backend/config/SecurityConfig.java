package com.hospital.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String DOCTOR = "DOCTOR";
    private static final String PATIENT = "PATIENT";

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/health/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                    // Doctor directory is needed when patients book appointments.
                    .requestMatchers(HttpMethod.GET, "/api/doctors").hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers("/api/doctors/**").hasRole(ADMIN)

                    // Patient Portal access for authenticated patients, assigned doctors and admin
                    .requestMatchers("/api/patients/me").hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/patients/*/history").hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/patients/*/family").hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers(HttpMethod.POST, "/api/patients/*/family").hasAnyRole(ADMIN, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/patients/*").hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers(HttpMethod.PUT, "/api/patients/*").hasAnyRole(ADMIN, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/patients").hasAnyRole(ADMIN, DOCTOR)
                    .requestMatchers("/api/patients/**").hasRole(ADMIN)

                    // APPOINTMENTS
                    .requestMatchers("/api/appointments/**").hasAnyRole(ADMIN, DOCTOR, PATIENT)

                    // PRESCRIPTIONS
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/prescriptions/patient/**",
                        "/api/prescriptions/*/pdf",
                        "/api/prescriptions/*"
                    ).hasAnyRole(ADMIN, DOCTOR, PATIENT)
                    .requestMatchers("/api/prescriptions/**").hasAnyRole(ADMIN, DOCTOR)

                    // INVOICES
                    .requestMatchers(HttpMethod.GET, "/api/invoices/patient/**").hasAnyRole(ADMIN, PATIENT)
                    .requestMatchers("/api/invoices/**").hasRole(ADMIN)

                    // CLINICAL RECORDS & ENCOUNTERS
                    .requestMatchers("/api/clinical/**").hasAnyRole(ADMIN, DOCTOR, PATIENT)

                    .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
