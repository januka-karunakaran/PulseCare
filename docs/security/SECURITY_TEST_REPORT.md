# PulseCare 2.0 — Security Test Report

**Execution Date:** 2026-11-19
**Feature Branch:** `fix/01.1.10-token-identity-corrections`
**Test Suite Target:** Spring Boot Backend Unit, Access Control, and Real MongoDB Integration Tests
**Execution Environment:** Windows 11, JDK 17.0.12, Maven Wrapper, Spring Boot 3.5.6, Local MongoDB 27017
**Result:** **40 TESTS RUN, 0 FAILURES, 0 ERRORS, 0 SKIPPED (BUILD SUCCESS)**

---

## 1. Executive Summary

Feature 01.1.10 implemented database-enforced atomic token rotation via MongoDB `findAndRemove`, unique token indexing, and dual-identity mapping (`Patient.id` vs `Patient.userId`) for appointments.

The backend test suite executes **40 tests across 9 test classes**, with a 100% pass rate.

| Test Class | Category | Tests Executed | Passed | Failed | Status |
|---|---|---|---|---|---|
| `RefreshTokenMongoIntegrationTest` | Real MongoDB Multi-Instance Concurrency Integration | 3 | 3 | 0 | **PASS** |
| `TokenLifecycleSecurityTest` | Rotation, Unit Invalidation, SHA-256 Hashing | 9 | 9 | 0 | **PASS** |
| `AccessControlSecurityTest` | BOLA / IDOR / Identity Mapping | 15 | 15 | 0 | **PASS** |
| `RegistrationSecurityTest` | Privilege Escalation (SEC-02) | 4 | 4 | 0 | **PASS** |
| `AppointmentServiceTest` | Patient/Doctor Identity Mapping & Schedule | 3 | 3 | 0 | **PASS** |
| `PrescriptionControllerTest` | Prescription Endpoint & PDF Generation | 1 | 1 | 0 | **PASS** |
| `JwtServiceTest` | JWT Signing & Parsing | 1 | 1 | 0 | **PASS** |
| `AuthServiceTest` | Authentication Logic | 2 | 2 | 0 | **PASS** |
| `BackendApplicationTests` | Spring Boot Context Loading | 2 | 2 | 0 | **PASS** |
| **Total** | | **40** | **40** | **0** | **100% PASS** |

---

## 2. Real MongoDB Multi-Instance Integration Evidence

### `RefreshTokenMongoIntegrationTest`
- **Execution Target:** Live standalone MongoDB connection (`localhost:27017/hospital_management_db`).
- **Tests Executed:**
  1. `concurrentRefreshRequests_AcrossTwoInstances_SharingSameDatabase_ExactlyOneSucceeds`:
     - Spawns two distinct `RefreshTokenServiceImpl` service instances sharing the same `MongoTemplate` and database.
     - Simulates 10 concurrent threads simultaneously requesting rotation of the same refresh token across both instances.
     - **Result:** Exactly 1 request succeeds; 9 requests fail with `BadRequestException("Invalid or revoked refresh token")`. Verified old token is deleted from MongoDB and replaced by a unique new token.
  2. `replayAttack_AcrossTwoInstances_SecondInstanceFails`:
     - First service instance consumes token; second service instance attempts replay and fails.
  3. `expiredToken_AcrossTwoInstances_RejectedAndPurged`:
     - Expired token is rejected and purged from the database.

---

## 3. Patient Appointment Identity Mapping Evidence

### `AppointmentServiceTest`
- **Tests Executed:**
  1. `getAppointmentsByPatientId_WhenUserIdDiffersFromPatientProfileId_ReturnsBothFormats`:
     - Patient profile ID (`id: "profile-patient-999"`) differs from User account ID (`userId: "user-account-456"`).
     - Appointments booked under either format are returned in own appointment listing via verified `findByPatientIdIn`.
  2. `getAppointmentsByDoctorId_WhenDoctorProfileIdDiffersFromUserId_ResolvesSchedule`:
     - Doctor profile ID (`id: "doc-profile-777"`) differs from account ID (`userId: "doc-user-888"`).
     - Resolves schedule across both keys.

---

## 4. Test Limitations & Operational Readiness

> [!WARNING]
> While real local MongoDB integration tests confirm document-level atomicity and multi-instance concurrency, **production certification requires end-to-end multi-node container load testing and replica set failover drills.**
