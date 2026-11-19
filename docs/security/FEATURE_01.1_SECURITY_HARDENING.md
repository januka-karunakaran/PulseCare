# Feature 01.1 — Security & Access Control Hardening Documentation

**Project:** PulseCare 2.0
**Phase:** 01 — Core Security Foundation
**Branch:** `feature/01.1-security-access-control` (followed by `fix/01.1.9-security-followup`, `fix/01.1.10-token-identity-corrections`, and `fix/01.1.11-test-database-isolation`)
**Base:** `main`
**Standard:** OWASP Top 10 API Security / HIPAA Security Rule

---

## 1. Executive Summary

Feature 01.1 addresses and resolves all critical and high-priority security vulnerabilities identified during the Feature 00.1 Baseline Audit:
- **SEC-01 (Critical):** Broken Object-Level Authorization (IDOR) across Appointments, Prescriptions, Invoices, Patients.
- **SEC-02 (Critical):** Public Registration Privilege Escalation to ADMIN.
- **SEC-03 (High):** Wildcard CORS and Unauthenticated WebSocket Origin/Channel Subscriptions.
- **SEC-04 (High):** Hardcoded Fallback JWT Secrets in Docker Compose and Application Properties.
- **SEC-05 (High):** Unauthenticated Public MongoDB Exposure on Host Network.
- **SEC-06 (High):** Broken Function-Level Authorization Locking Patients Out of the Patient Portal.
- **SEC-07 (High):** Incomplete Token Lifecycle (Lack of Token Rotation & Lack of Revocation on Logout).

Features 01.1.9 through 01.1.11 finalized database-enforced atomicity (replacing JVM-local synchronization with MongoDB `findAndRemove` and unique indexes), full patient dual-identity mapping (`id` vs `userId`), same-second JWT invalidation precision, isolated Testcontainers MongoDB integration testing with fail-closed destructive safeguards, and production index deployment policies.

---

## 2. Remediations Implemented

### 2.1 Feature 01.1.1: Registration Privilege Escalation Fix (SEC-02)
- `SignupRequest.java`: Removed client role selection.
- `AuthServiceImpl.java`: Rejects non-`PATIENT` role requests with `BadRequestException`. Defaults role unconditionally to `Role.PATIENT` and persists a linked `Patient` document.
- `frontend/app/register/page.jsx`: Removed public role-selection UI controls. Registration registers exclusively as Patient.

### 2.2 Features 01.1.2 & 01.1.3: Patient Data Ownership & Portal Access Repair (SEC-01, SEC-06)
- `AuthorizationService.java`: Fine-grained object ownership checks.
- `SecurityConfig.java`: Allowed `ROLE_PATIENT` and `ROLE_DOCTOR` on portal endpoints.
- `AppointmentController.java`, `PrescriptionController.java`, `InvoiceController.java`, `PatientController.java`: Protected with `@PreAuthorize` guards.

### 2.3 Feature 01.1.4: JWT & Refresh Token Hardening (SEC-04, SEC-07)
- Added `tokenInvalidBefore` field on `User`.
- Validates minimum 256 bits secret entropy on production startup.
- Invalidation on logout.

### 2.4 Feature 01.1.5: CORS & WebSocket Security (SEC-03)
- Explicit CORS origin restrictions.
- Inbound STOMP `ChannelInterceptor` validating JWT on CONNECT and restricting SUBSCRIBE destinations.
- Modernized frontend WebSocket client to `@stomp/stompjs` with Bearer auth.

### 2.5 Feature 01.1.6: MongoDB & Environment Configuration (SEC-05)
- Localhost port binding and root credentials for MongoDB in development Compose.
- Complete internal exposure only (`expose: 27017`) for production Compose.
- Sanitized environment templates and application properties.

### 2.6 Features 01.1.9, 01.1.10 & 01.1.11: Token Concurrency, Identity, and Test Database Isolation
- **Database-Enforced Atomic Refresh Token Rotation:**
  - Replaced JVM-local locks with MongoDB `mongoTemplate.findAndRemove(query, RefreshToken.class)`.
  - Added `@Indexed(unique = true)` on `RefreshToken.token`.
  - Retained SHA-256 token hashing at rest.
  - Verified cross-instance atomicity with multi-threaded live MongoDB integration tests.
- **Patient Dual-Identity Mapping:**
  - Configured `Patient` model with `@Id private String id` and `private String userId`.
  - Scoped `getAppointmentsByPatientId` to resolve both profile ID and user ID before querying MongoDB via `findByPatientIdIn`.
  - Updated `AuthorizationService.isPatientOwner` to recognize both ID formats without email dependency.
- **Same-Second JWT Logout Precision:**
  - Added `iat_ms` millisecond claim to JWT payload to resolve second-boundary truncation.
- **Safe Database Integration Testing (01.1.11):**
  - Configured dedicated test database `hospital_management_test_isolated` via `application-test.properties`.
  - Added Testcontainers MongoDB dynamic containerization.
  - Implemented fail-closed safety guard throwing `IllegalStateException` if integration tests ever run against non-test or production databases.
  - Environment-specific index policy: `auto-index-creation=true` in dev/test, `false` in production.
  - Enforced strict CI quality gates in GitHub Actions workflows.

---

## 3. Verification & Evidence
- **Automated Regression Suite:** 41 tests run, 41 passed, 0 failed.
- **Isolated MongoDB Integration Suite:** `RefreshTokenMongoIntegrationTest` (4 tests passed).
- **Unit & Access Control Tests:** 37 unit and MockMvc tests passed.
- **Frontend Linting:** 0 errors.
