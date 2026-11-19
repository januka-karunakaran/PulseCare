# Feature 01.1 — Security & Access Control Hardening Documentation

**Project:** PulseCare 2.0
**Phase:** 01 — Core Security Foundation
**Branch:** `feature/01.1-security-access-control`
**Base:** `main` (commit `8d325c496dda57dc5fc2d10f0bf202fda02cad2c`)
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

The implementation strictly maintains PulseCare as a functioning hospital management application while establishing enterprise-grade healthcare security boundaries.

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
    - `isPatientOwner(patientId)`: Verifies caller's authenticated user ID matches the patient entity's linked `userId`.
    - `canAccessPatient(patientId)`: Permits access if caller is `ADMIN`, the owner patient, or an assigned doctor with a clinical relationship.
    - `hasDoctorPatientRelationship(doctorId, patientId)`: Verifies whether an appointment exists between the doctor and patient in MongoDB.
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

---

## 3. Verification & Evidence
- **Automated Regression Suite:** 29 tests run, 29 passed, 0 failed.
- **Security Tests:**
  - `RegistrationSecurityTest`: 4/4 passed.
  - `AccessControlSecurityTest`: 12/12 passed.
  - `TokenLifecycleSecurityTest`: 6/6 passed.
- **Frontend Linting:** 0 errors.
