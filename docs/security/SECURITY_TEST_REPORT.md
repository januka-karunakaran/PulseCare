# PulseCare 2.0 — Security Test Report

**Execution Date:** 2026-11-19
**Feature Branch:** `fix/01.1.9-security-followup`
**Test Suite Target:** Spring Boot Backend Integration & Security Tests
**Execution Environment:** Windows 11, JDK 17.0.12, Maven Wrapper, Spring Boot 3.5.6
**Result:** **35 TESTS RUN, 0 FAILURES, 0 ERRORS, 0 SKIPPED (BUILD SUCCESS)**

---

## 1. Executive Summary

Feature 01.1.9 applied critical post-merge security and functional corrections across refresh token atomicity, account-to-patient identity mapping, appointment identifier mapping, and same-second JWT logout precision.

The updated test suite now executes **35 tests**, all passing with zero failures.

| Test Class | Category | Tests Executed | Passed | Failed | Status |
|---|---|---|---|---|---|
| `RegistrationSecurityTest` | Privilege Escalation (SEC-02) | 4 | 4 | 0 | **PASS** |
| `AccessControlSecurityTest` | BOLA / IDOR / Identity Mapping (SEC-01, SEC-06) | 15 | 15 | 0 | **PASS** |
| `TokenLifecycleSecurityTest` | Rotation, Concurrency, Invalidation (SEC-04, SEC-07) | 9 | 9 | 0 | **PASS** |
| `PrescriptionControllerTest` | Prescription Endpoint & PDF Generation | 1 | 1 | 0 | **PASS** |
| `JwtServiceTest` | JWT Signing & Parsing | 1 | 1 | 0 | **PASS** |
| `AppointmentServiceTest` | Appointment Business Logic | 1 | 1 | 0 | **PASS** |
| `AuthServiceTest` | Authentication Logic | 2 | 2 | 0 | **PASS** |
| `BackendApplicationTests` | Spring Boot Context Loading | 2 | 2 | 0 | **PASS** |
| **Total** | | **35** | **35** | **0** | **100% PASS** |

---

## 2. Post-Merge Follow-up Verification Details (Feature 01.1.9)

### A. Refresh Token Concurrency & Atomicity (SEC-07 Follow-up)
- **Files:** `RefreshTokenServiceImpl.java`, `RefreshTokenRepository.java`, `TokenLifecycleSecurityTest.java`
- **Tests Added/Verified:**
  1. `concurrentRefreshRequests_OnlyOneSucceeds_OthersFail`: Simulates 10 concurrent threads racing to consume the exact same refresh token. Verified that exactly 1 request succeeds in rotation, while 9 requests are rejected with `BadRequestException` ("Invalid or revoked refresh token").
  2. `createRefreshToken_StoresHashedTokenInDatabase`: Verified that token values are hashed using SHA-256 before being persisted to MongoDB (raw tokens are never stored at rest in plaintext).
  3. `rotateRefreshToken_ReplayOfRevokedToken_ThrowsBadRequestException`: Verified replay attacks fail.
  4. `rotateRefreshToken_ExpiredToken_ThrowsBadRequestExceptionAndCleansUp`: Verified expired tokens are purged.

### B. Stable Account-to-Patient Identity Mapping (SEC-01 Follow-up)
- **Files:** `AuthorizationService.java`, `AccessControlSecurityTest.java`
- **Tests Added/Verified:**
  1. `patientWithChangedOrForgedEmail_CannotAccessOtherPatientRecord`: Verified that changing or forging an email address to match another patient's email does not confer medical record access. Access is strictly bound to immutable account `userId`.
  2. `patientA_AccessOwnMedicalHistory_Succeeds` & `patientB_AccessOtherPatientMedicalHistory_Forbidden`: Verified record-level isolation between distinct patient accounts.

### C. Appointment Identifier & Role Scoping (SEC-01 & SEC-06 Follow-up)
- **Files:** `AppointmentController.java`, `AppointmentServiceImpl.java`, `PrescriptionController.java`, `AccessControlSecurityTest.java`
- **Tests Added/Verified:**
  1. `doctorOwnershipCheck_SupportsBothProfileIdAndUserId`: Verified doctors can query schedules using either MongoDB Doctor profile ID or User account ID.
  2. `patientAttemptingToQueryDoctorAppointments_ReturnsFalse`: Verified patients cannot query doctor appointment lists.
  3. Scoped appointment retrieval ensures patients see only their appointments and doctors see only their scheduled appointments.

### D. Same-Second JWT Logout & Invalidation Precision (SEC-07 Follow-up)
- **Files:** `JwtService.java`, `AuthController.java`, `TokenLifecycleSecurityTest.java`
- **Tests Added/Verified:**
  1. `sameSecondLogoutAndImmediateReLogin_NewTokenIsValidAndOldTokenIsInvalid`: Validates millisecond precision (`iat_ms` claim) so that logging out and immediately re-logging in within the same second revokes the prior token while ensuring the new session remains active and valid.
  2. `accessToken_IssuedBeforeLogoutTimestamp_IsInvalid`: Validates that tokens issued before `tokenInvalidBefore` are rejected across distributed calls.

---

## 3. Test Limitations & Scope Clarification

> [!WARNING]
> While unit and mock-backed integration tests verify the business logic, atomicity primitives, and security filters, **full production certification requires end-to-end load testing on a clustered MongoDB replica set and multi-node container deployment.**
