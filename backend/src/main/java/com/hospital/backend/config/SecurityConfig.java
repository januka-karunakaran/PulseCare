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
    private static final String LAB_TECH = "LAB_TECH";
    private static final String PHARMACIST = "PHARMACIST";
    private static final String BILLING_STAFF = "BILLING_STAFF";

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

                    // LABORATORY INTEGRATION
                    .requestMatchers(HttpMethod.GET, "/api/lab/tests/**").hasAnyRole(ADMIN, DOCTOR, LAB_TECH, PATIENT)
                    .requestMatchers("/api/lab/tests/**").hasRole(ADMIN)

                    .requestMatchers(HttpMethod.POST, "/api/lab/orders").hasAnyRole(ADMIN, DOCTOR)
                    .requestMatchers(HttpMethod.POST, "/api/lab/orders/*/cancel").hasAnyRole(ADMIN, DOCTOR)
                    .requestMatchers(HttpMethod.GET, "/api/lab/orders/**").hasAnyRole(ADMIN, DOCTOR, LAB_TECH, PATIENT)

                    .requestMatchers("/api/lab/orders/*/specimens/**", "/api/lab/specimens/**").hasAnyRole(ADMIN, DOCTOR, LAB_TECH)

                    .requestMatchers(HttpMethod.POST, "/api/lab/results/*/verify", "/api/lab/results/*/release").hasAnyRole(ADMIN, DOCTOR)
                    .requestMatchers(HttpMethod.POST, "/api/lab/results/*/correct").hasAnyRole(ADMIN, DOCTOR)
                    .requestMatchers(HttpMethod.POST, "/api/lab/orders/*/results", "/api/lab/results/*/draft", "/api/lab/results").hasAnyRole(ADMIN, DOCTOR, LAB_TECH)
                    .requestMatchers(HttpMethod.PUT, "/api/lab/results/*/draft").hasAnyRole(ADMIN, DOCTOR, LAB_TECH)
                    .requestMatchers(HttpMethod.GET, "/api/lab/results/**").hasAnyRole(ADMIN, DOCTOR, LAB_TECH, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/lab/patients/*/reports").hasAnyRole(ADMIN, DOCTOR, PATIENT)

                    // PHARMACY & MEDICATION DISPENSING
                    .requestMatchers(HttpMethod.GET, "/api/pharmacy/medicines/**").hasAnyRole(ADMIN, DOCTOR, PHARMACIST, PATIENT)
                    .requestMatchers("/api/pharmacy/medicines/**").hasAnyRole(ADMIN, PHARMACIST)
                    .requestMatchers("/api/pharmacy/inventory/**", "/api/pharmacy/batches/**").hasAnyRole(ADMIN, PHARMACIST)
                    .requestMatchers("/api/pharmacy/queue/**").hasAnyRole(ADMIN, PHARMACIST)
                    .requestMatchers(HttpMethod.POST, "/api/pharmacy/dispense").hasAnyRole(ADMIN, PHARMACIST)
                    .requestMatchers(HttpMethod.POST, "/api/pharmacy/reverse").hasAnyRole(ADMIN, PHARMACIST)
                    .requestMatchers(HttpMethod.GET, "/api/pharmacy/dispense/**").hasAnyRole(ADMIN, DOCTOR, PHARMACIST, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/pharmacy/patients/*/dispenses").hasAnyRole(ADMIN, DOCTOR, PHARMACIST, PATIENT)
                    .requestMatchers("/api/pharmacy/movements/**").hasAnyRole(ADMIN, PHARMACIST)

                    // BILLING & FINANCIAL OPERATIONS
                    .requestMatchers(HttpMethod.GET, "/api/billing/services/**").hasAnyRole(ADMIN, BILLING_STAFF, DOCTOR, PATIENT)
                    .requestMatchers("/api/billing/services/**").hasAnyRole(ADMIN, BILLING_STAFF)
                    .requestMatchers("/api/billing/charges/**").hasAnyRole(ADMIN, BILLING_STAFF)
                    .requestMatchers(HttpMethod.GET, "/api/billing/invoices/**").hasAnyRole(ADMIN, BILLING_STAFF, DOCTOR, PATIENT)
                    .requestMatchers("/api/billing/invoices/**").hasAnyRole(ADMIN, BILLING_STAFF)
                    .requestMatchers("/api/billing/payments/**").hasAnyRole(ADMIN, BILLING_STAFF, PATIENT)
                    .requestMatchers("/api/billing/refunds/**", "/api/billing/credit-notes/**").hasAnyRole(ADMIN, BILLING_STAFF)
                    .requestMatchers("/api/billing/reports/**", "/api/billing/reconciliation").hasAnyRole(ADMIN, BILLING_STAFF)
                    .requestMatchers("/api/billing/my-invoices", "/api/billing/my-payments").hasAnyRole(ADMIN, BILLING_STAFF, PATIENT)
                    .requestMatchers(HttpMethod.GET, "/api/invoices/**").hasAnyRole(ADMIN, BILLING_STAFF, PATIENT)
                    .requestMatchers("/api/invoices/**").hasAnyRole(ADMIN, BILLING_STAFF)

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
