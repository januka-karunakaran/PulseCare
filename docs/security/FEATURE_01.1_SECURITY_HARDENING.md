# Feature 01.1 — Security & Access Control Hardening Documentation

**Project:** PulseCare 2.0
**Phase:** 01 — Core Security Foundation
**Branch:** `feature/01.1-security-access-control` (and `fix/01.1.9-security-followup`)
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

Feature 01.1.9 delivers post-merge follow-up corrections for refresh token atomicity/hashing, immutable account-to-patient identity mapping (removing email trust), appointment/prescription doctor profile ID vs user ID resolution, and same-second JWT logout precision.

---

## 2. Remediations Implemented

### 2.1 Feature 01.1.1: Registration Privilege Escalation Fix (SEC-02)
- **Problem:** Public registration endpoint accepted `"role": "ADMIN"`, enabling arbitrary external privilege escalation.
- **Backend Fix:**
  - `SignupRequest.java`: Removed role requirement; made role optional and ignored/validated.
  - `AuthServiceImpl.java`: Public signup strictly rejects any request specifying a non-`PATIENT` role with `BadRequestException` ("Public registration is restricted to PATIENT role only"). Defaults role unconditionally to `Role.PATIENT` and persists a linked `Patient` document.
- **Frontend Fix:**
  - `frontend/app/register/page.jsx`: Removed public role-selection UI controls (Doctor and Admin buttons/icons). Registration now registers exclusively as a Patient.

### 2.2 Features 01.1.2 & 01.1.3: Patient Data Ownership & Portal Access Repair (SEC-01, SEC-06)
- **Problem:**
  - Patients could read other patients' records by altering identifiers (`patientId`, appointment ID, prescription ID).
  - In `SecurityConfig.java`, `/api/patients/**` required `ROLE_ADMIN`, locking patients out of `/api/patients/me`, medical history, and family records.
- **Remediation Architecture:**
  - **`AuthorizationService.java` (`@Service("authz")`):**
    - `isPatientOwner(patientId)`: Verifies caller's authenticated user ID matches the patient entity's linked `userId`. Never trusts matching email addresses as proof of ownership.
    - `canAccessPatient(patientId)`: Permits access if caller is `ADMIN`, the owner patient, or an assigned doctor with a clinical relationship.
    - `hasDoctorPatientRelationship(doctorId, patientId)`: Verifies whether an appointment exists between the doctor and patient in MongoDB across profile ID and user ID.
    - `checkAppointmentAccess(appointment)`: Verifies patient ownership or doctor assignment.
    - `canUpdateAppointmentStatus(appointment, newStatus)`: Allows patients to only cancel their own appointment.
    - `checkPrescriptionAccess(prescription)`: Verifies patient ownership or doctor authorship.
    - `checkInvoiceAccess(invoice)`: Verifies patient ownership.
  - **Endpoint Guards:**
    - `SecurityConfig.java`: Allowed `ROLE_PATIENT` and `ROLE_DOCTOR` on `/api/patients/me`, `/api/patients/{id}/history`, `/api/patients/{id}/family`, `/api/invoices/patient/**`, and `/api/prescriptions/patient/**`.
    - `PatientController.java`, `AppointmentController.java`, `PrescriptionController.java`, `InvoiceController.java`, `BedController.java`, `UserController.java`: Added `@PreAuthorize` annotations and fine-grained programmatic authorization checks.
    - `AppointmentController.getAllAppointments`: Auto-scopes results by role (patients only see their appointments; doctors only see their appointments; admins see all).
    - `SearchController.java`: Auto-scopes search filters to patient's own ID.

### 2.3 Feature 01.1.4: JWT & Refresh Token Hardening (SEC-04, SEC-07)
- **Problem:**
  - JWT secret had default fallback values in configuration files.
  - Refresh tokens were not rotated on refresh (replay vulnerability).
  - Logout did not invalidate access tokens.
- **Remediation Architecture:**
  - `User.java`: Added `tokenInvalidBefore` field (`Instant`).
  - `JwtService.java`:
    - Validates `tokenInvalidBefore`: If token was issued before this timestamp, `isTokenValid` returns `false`.
    - Added `validateSecretKey` to verify minimum 256 bits of entropy on production startup.
  - `RefreshTokenService.java` & `RefreshTokenServiceImpl.java`:
    - Added `rotateRefreshToken(rawToken)`: Generates a new refresh token and deletes/invalidates the old token in a single atomic flow.
  - `AuthController.java`:
    - `/refresh`: Returns rotated refresh token and new access token.
    - `/logout`: Sets `tokenInvalidBefore = Instant.now()` on the user and deletes active refresh tokens in MongoDB.
  - `frontend/services/api.js` & `frontend/utils/auth.js`: Updated frontend to store rotated refresh tokens.

### 2.4 Feature 01.1.5: CORS & WebSocket Security (SEC-03)
- **Problem:**
  - CORS configuration used `allowedOriginPatterns("*")` with credentials.
  - WebSocket endpoint allowed wildcard origins and had no channel subscription authorization.
- **Remediation Architecture:**
  - `CorsConfig.java`: Restricts CORS strictly to explicit configured origins (`cors.allowed-origins`).
  - `WebSocketConfig.java`:
    - Configured explicit origins on `/ws/notifications`.
    - Added `ChannelInterceptor` on inbound client channel:
      - Validates JWT token on `StompCommand.CONNECT`.
      - Validates destination on `StompCommand.SUBSCRIBE`: Users can only subscribe to their own notification channel (`/topic/notifications/{userId}`).
  - `frontend/hooks/useWebSocket.js`: Converted implementation to `@stomp/stompjs` + `sockjs-client` with authenticated STOMP headers (`Authorization: Bearer <token>`).

### 2.5 Feature 01.1.6: MongoDB & Environment Configuration (SEC-05)
- **Problem:**
  - MongoDB published port `27017` on host with no root credentials in `docker-compose.yml`.
  - Sensitive fallback email and secrets in `application.properties`.
- **Remediation Architecture:**
  - `docker-compose.yml`:
    - Bound MongoDB port strictly to localhost: `127.0.0.1:27017:27017`.
    - Configured root authentication environment variables (`MONGO_INITDB_ROOT_USERNAME`, `MONGO_INITDB_ROOT_PASSWORD`).
    - Removed fallback JWT secret and wildcard origins.
  - `docker-compose.prod.yml`:
    - Removed host port publishing entirely (`expose: 27017` internal only).
  - `.env.example`: Replaced hardcoded credentials with safe placeholder variables.
  - `application.properties` & `application-prod.properties`: Removed personal email addresses and weak default secrets.

### 2.6 Feature 01.1.9: Post-Merge Follow-up Corrections
- **Refresh Token Atomicity & Storage Security:**
  - Implemented atomic `deleteByToken` in `RefreshTokenRepository` combined with synchronized locks in `RefreshTokenServiceImpl` to guarantee only one request can consume a token during concurrent races.
  - All refresh tokens stored in MongoDB are hashed using SHA-256 (plaintext tokens are never persisted).
- **Immutable Account-to-Patient Identity Mapping:**
  - `AuthorizationService.isPatientOwner` strictly checks `user.getId() == patient.getUserId()`. Removed email matching fallback to prevent cross-account leakage if an email is altered or spoofed.
- **Doctor & Patient ID Mapping in Appointments:**
  - Resolved `AppointmentServiceImpl` and `AuthorizationService.isDoctorOwner` to support both Doctor MongoDB ID and User account ID.
  - `findByDoctorIdIn` and `findByPatientIdIn` ensure all legitimate appointments are retrieved without exposing unrelated data.
- **JWT Logout Precision:**
  - Added `iat_ms` millisecond claim to JWT tokens. Resolves same-second logout and immediate re-login truncation issues so that old tokens are revoked while newly issued tokens remain valid.

---

## 3. Verification & Evidence
- **Automated Regression Suite:** 35 tests run, 35 passed, 0 failed.
- **Security Tests:**
  - `RegistrationSecurityTest`: 4/4 passed.
  - `AccessControlSecurityTest`: 15/15 passed.
  - `TokenLifecycleSecurityTest`: 9/9 passed.
- **Frontend Linting:** 0 errors.
