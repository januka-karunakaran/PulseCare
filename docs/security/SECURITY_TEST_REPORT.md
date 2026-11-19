# PulseCare 2.0 — Security Test Report

**Execution Date:** 2026-11-19
**Feature Branch:** `feature/01.1-security-access-control`
**Test Suite Target:** Spring Boot Backend Integration & Security Tests
**Execution Environment:** Windows 11, JDK 17.0.12, Maven Wrapper, Spring Boot 3.5.6
**Result:** **29 TESTS RUN, 0 FAILURES, 0 ERRORS, 0 SKIPPED (BUILD SUCCESS)**

---

## 1. Executive Summary

All critical security vulnerabilities identified in Feature 00.1 baseline audit (SEC-01 through SEC-07) have been verified with automated regression tests. The entire backend test suite was executed via `./mvnw test` and completed with zero failures.

| Test Class | Category | Tests Executed | Passed | Failed | Status |
|---|---|---|---|---|---|
| `RegistrationSecurityTest` | Privilege Escalation (SEC-02) | 4 | 4 | 0 | **PASS** |
| `AccessControlSecurityTest` | BOLA / IDOR / Portal Lock-out (SEC-01, SEC-06) | 12 | 12 | 0 | **PASS** |
| `TokenLifecycleSecurityTest` | Token Rotation, Invalidation, Revocation (SEC-04, SEC-07) | 6 | 6 | 0 | **PASS** |
| `PrescriptionControllerTest` | Prescription Endpoint & PDF Generation | 1 | 1 | 0 | **PASS** |
| `JwtServiceTest` | JWT Signing & Parsing | 1 | 1 | 0 | **PASS** |
| `AppointmentServiceTest` | Appointment Business Logic | 1 | 1 | 0 | **PASS** |
| `AuthServiceTest` | Authentication Logic | 2 | 2 | 0 | **PASS** |
| `BackendApplicationTests` | Spring Boot Context Loading | 2 | 2 | 0 | **PASS** |
| **Total** | | **29** | **29** | **0** | **100% PASS** |

---

## 2. Test Class Details & Security Evidence

### A. `RegistrationSecurityTest` (SEC-02 Verification)
- **File:** `backend/src/test/java/com/hospital/backend/security/RegistrationSecurityTest.java`
- **Tests Executed:**
  1. `publicSignup_AsPatient_Succeeds`: Verified public registration creates `Role.PATIENT` with HTTP 201 Created.
  2. `publicSignup_AttemptingAdminRole_IsRejected`: Submitting `"role": "ADMIN"` throws `BadRequestException` and returns HTTP 400.
  3. `publicSignup_AttemptingDoctorRole_IsRejected`: Submitting `"role": "DOCTOR"` throws `BadRequestException` and returns HTTP 400.
  4. `publicSignup_WithNoRoleSpecified_DefaultsToPatient`: Submitting registration without a role creates `Role.PATIENT` with HTTP 201 Created.

### B. `AccessControlSecurityTest` (SEC-01 & SEC-06 Verification)
- **File:** `backend/src/test/java/com/hospital/backend/security/AccessControlSecurityTest.java`
- **Tests Executed:**
  1. `patientPortal_GetMe_SucceedsForPatient`: Patient calling `/api/patients/me` returns HTTP 200 and own profile (`SEC-06` fix).
  2. `patient_AccessOwnMedicalHistory_Succeeds`: Patient calling `/api/patients/{ownId}/history` returns HTTP 200.
  3. `patient_AccessOtherPatientMedicalHistory_Forbidden`: Patient A calling `/api/patients/{patientBId}/history` returns HTTP 403 Forbidden (`SEC-01` fix).
  4. `patient_AccessOwnFamilyMembers_Succeeds`: Patient calling `/api/patients/{ownId}/family` returns HTTP 200.
  5. `patient_AccessOtherPatientFamilyMembers_Forbidden`: Patient A calling `/api/patients/{patientBId}/family` returns HTTP 403 Forbidden (`SEC-01` fix).
  6. `patient_ViewOwnAppointment_Succeeds`: Patient calling `/api/appointments/{ownAppointmentId}` returns HTTP 200.
  7. `patient_ViewOtherPatientAppointment_Forbidden`: Patient A calling `/api/appointments/{patientBAppointmentId}` returns HTTP 403 Forbidden (`SEC-01` fix).
  8. `patient_CancelOwnAppointment_Succeeds`: Patient calling `PUT /api/appointments/{ownId}/status?status=CANCELLED` returns HTTP 200.
  9. `patient_CancelOtherPatientAppointment_Forbidden`: Patient A attempting to cancel Patient B's appointment returns HTTP 403 Forbidden.
  10. `patient_DownloadOwnPrescriptionPdf_Succeeds`: Patient calling `/api/prescriptions/{ownPrescriptionId}/pdf` returns HTTP 200 and PDF binary.
  11. `patient_DownloadOtherPatientPrescriptionPdf_Forbidden`: Patient A attempting to download Patient B's prescription PDF returns HTTP 403 Forbidden (`SEC-01` fix).
  12. `patient_ViewOtherPatientInvoice_Forbidden`: Patient A attempting to query Patient B's invoices returns HTTP 403 Forbidden (`SEC-01` fix).

### C. `TokenLifecycleSecurityTest` (SEC-04 & SEC-07 Verification)
- **File:** `backend/src/test/java/com/hospital/backend/security/TokenLifecycleSecurityTest.java`
- **Tests Executed:**
  1. `refreshToken_RotatesSuccessfully_OldTokenInvalidated`: Verifies that refreshing returns a new refresh token and deletes/invalidates the old refresh token (`SEC-07` fix).
  2. `refreshToken_ReplayAttack_IsRejected`: Verifies that attempting to reuse an already-consumed refresh token fails with HTTP 400 Bad Request.
  3. `logout_InvalidatesRefreshToken_AndAccessTokens`: Calling `/api/auth/logout` updates user `tokenInvalidBefore` and purges active refresh tokens.
  4. `jwtService_RejectsTokenIssuedBeforeInvalidation`: Validates that access tokens issued prior to `tokenInvalidBefore` fail validation (`isTokenValid == false`), providing instant cross-instance access token revocation.
  5. `jwtService_ValidatesValidToken`: Validates that valid unexpired tokens for active users pass validation.
  6. `jwtService_ProductionKeyValidation_RequiresAtLeast256Bits`: Ensures production profile requires at least 256 bits of entropy and rejects weak keys (`SEC-04` fix).

---

## 3. Frontend & Static Analysis Evidence

- **Frontend ESLint Check:**
  - Command: `cd frontend; npm run lint`
  - Output: `0 errors, 11 warnings` (clean static pass, missing `FaShieldHalved` import resolved).
- **Environment & Secret Audit:**
  - Audited `.env.example`, `application.properties`, and `application-prod.properties`.
  - Confirmed: 0 hardcoded credentials, 0 real JWT secrets, 0 hardcoded personal emails in committed configs.
- **Docker Compose Configurations:**
  - `docker-compose.yml`: MongoDB bound to `127.0.0.1:27017:27017` with root authentication required (`SEC-05`). Wildcard origins and fallback JWT secret removed (`SEC-03`, `SEC-04`).
  - `docker-compose.prod.yml`: MongoDB completely unexposed to host (`expose: 27017`), internal communication only (`SEC-05`).
