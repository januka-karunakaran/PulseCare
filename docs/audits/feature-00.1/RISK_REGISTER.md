# ðŸ›¡ï¸ PulseCare 2.0 â€” Security & Architectural Risk Register

**Audit Target:** PulseCare Existing System Baseline
**Audited Commit:** `be6be662c1b50e58d5fe77439af2ce14e4f4f655`
**Standard:** OWASP Top 10 API Security / HIPAA Security Rule / NIST Cybersecurity Framework

---

## Risk Summary

| Risk ID | Severity | Category | Affected Component | Impact | Verification Status |
|---|---|---|---|---|---|
| **SEC-01** | **CRITICAL** | Broken Object-Level Authorization (IDOR) | `AppointmentController.java`, `PrescriptionController.java`, `SecurityConfig.java` | Cross-patient health record leakage, unauthorized cancellation | Confirmed |
| **SEC-02** | **CRITICAL** | Broken Object Property-Level Authorization | `SignupRequest.java`, `AuthServiceImpl.java`, `register/page.jsx` | Unrestricted privilege escalation to `ADMIN` | Confirmed |
| **SEC-03** | **HIGH** | Security Misconfiguration | `CorsConfig.java`, `WebSocketConfig.java`, `DashboardController.java` | Credentialed CORS hijacking, cross-site WebSocket data leak | Confirmed |
| **SEC-04** | **HIGH** | Hardcoded Credentials & Secrets | `docker-compose.yml`, `application.properties` | Token forgery, offline signature cracking | Confirmed |
| **SEC-05** | **HIGH** | Infrastructure Exposure | `docker-compose.yml` | Unauthenticated public MongoDB port exposure | Confirmed |
| **SEC-06** | **HIGH** | Broken Function-Level Authorization | `SecurityConfig.java`, `PatientController.java` | Patient portal lock-out (`/api/patients/me` returns 403 Forbidden) | Confirmed |
| **SEC-07** | **HIGH** | Token Management & Invalidation Flaws | `AuthController.java`, `JwtService.java`, `RefreshTokenServiceImpl.java` | Lack of token revocation, replay attacks, non-rotation | Confirmed |
| **ARCH-01**| **HIGH** | Concurrency & Race Conditions | `AppointmentServiceImpl.java`, `BedServiceImpl.java`, `Bed.java` | Double-booking beds and appointment slots under concurrent load | Confirmed |
| **ARCH-02**| **HIGH** | Scalability & Memory Exhaustion | `SearchServiceImpl.java`, `ReportsPage.jsx` | Server out-of-memory errors from unbounded in-memory `findAll()` | Confirmed |
| **ARCH-03**| **MEDIUM** | Phantom Features & Dead Code | `TokenCleanupScheduler.java`, `AppointmentReminderScheduler.java`, `event/*` | System maintenance fails, false security sense from docs | Confirmed |
| **ARCH-04**| **MEDIUM** | Real-Time Protocol Incompatibility | `frontend/hooks/useWebSocket.js`, `WebSocketConfig.java` | Broken real-time doctor/patient notifications | Confirmed |
| **ARCH-05**| **MEDIUM** | Inconsistent Exception Handling | `AppointmentServiceImpl.java`, `BedServiceImpl.java`, `GlobalExceptionHandler.java` | Internal 500 errors returned for normal client 404/400 errors | Confirmed |

---

## Detailed Risk Assessments

### SEC-01: Cross-Patient IDOR & Broken Object-Level Authorization (BOLA)
- **Risk ID:** `SEC-01`
- **Severity:** **CRITICAL** (CVSS 9.1)
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/config/SecurityConfig.java` (lines 48, 51-56)
  - `backend/src/main/java/com/hospital/backend/controller/AppointmentController.java` (lines 30, 39, 44, 52)
  - `backend/src/main/java/com/hospital/backend/controller/PrescriptionController.java` (lines 44, 60)
  - `backend/src/main/java/com/hospital/backend/controller/InvoiceController.java` (line 31)
- **Evidence:**
  In `SecurityConfig.java`:
  ```java
  .requestMatchers("/api/appointments/**").hasAnyRole(ADMIN, DOCTOR, PATIENT)
  .requestMatchers(HttpMethod.GET, "/api/prescriptions/patient/**", "/api/prescriptions/*/pdf").hasAnyRole(ADMIN, DOCTOR, PATIENT)
  ```
  Neither `AppointmentController` nor `AppointmentServiceImpl` checks whether the authenticated patient ID matches the requested `patientId` or `id`. Any patient can call `GET /api/appointments/patient/{anyPatientId}`, `GET /api/prescriptions/patient/{anyPatientId}`, `GET /api/prescriptions/{anyId}/pdf`, or `PUT /api/appointments/{id}/status?status=CANCELLED`.
- **Impact:** Complete exposure of protected health information (PHI) violating HIPAA and GDPR. Unauthorized cancellation or alteration of appointments.
- **Safe Recommendation:** Enforce record-level ownership checks using `@PreAuthorize("@securityUtils.isCurrentPatient(#patientId)")` or verify ownership in service layers before returning entities.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### SEC-02: Public Self-Registration Privilege Escalation to ADMIN
- **Risk ID:** `SEC-02`
- **Severity:** **CRITICAL** (CVSS 9.8)
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/dto/SignupRequest.java` (line 22)
  - `backend/src/main/java/com/hospital/backend/service/impl/AuthServiceImpl.java` (lines 93-94)
  - `frontend/app/register/page.jsx` (lines 22-26)
- **Evidence:**
  `SignupRequest` allows the caller to submit `"role": "ADMIN"`. `AuthServiceImpl` constructs the user with:
  ```java
  .role(Role.valueOf(request.getRole().toUpperCase()))
  ```
  The frontend `register/page.jsx` explicitly offers an "Admin" role selection button in the public registration form.
- **Impact:** Any external actor can register an account with full administrative rights over the entire clinical system.
- **Safe Recommendation:** Disallow `role` in `SignupRequest`. Force public registration to default strictly to `Role.PATIENT`. Admin accounts must only be created via secured administrative provisioning or CLI.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### SEC-03: Permissive CORS & Wildcard WebSocket Origins
- **Risk ID:** `SEC-03`
- **Severity:** **HIGH** (CVSS 8.2)
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/config/CorsConfig.java` (lines 22-29)
  - `backend/src/main/java/com/hospital/backend/config/WebSocketConfig.java` (line 26)
  - `backend/src/main/java/com/hospital/backend/controller/DashboardController.java` (line 20)
  - `backend/src/main/java/com/hospital/backend/controller/NotificationController.java` (line 19)
- **Evidence:**
  `CorsConfig.java`:
  ```java
  config.setAllowCredentials(true);
  config.setAllowedOriginPatterns(List.of("*"));
  ```
  `WebSocketConfig.java`:
  ```java
  registry.addEndpoint("/ws/notifications").setAllowedOriginPatterns("*").withSockJS();
  ```
- **Impact:** Allows malicious external websites in a user's browser to send authenticated cross-origin requests and read sensitive medical and operational data.
- **Safe Recommendation:** Bind CORS and WebSocket allowed origins strictly to the configured domain properties (`cors.allowed-origins`). Remove wildcard `@CrossOrigin` annotations from controllers.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### SEC-04: Default Fallback JWT Secret in Compose & Properties
- **Risk ID:** `SEC-04`
- **Severity:** **HIGH** (CVSS 7.8)
- **Affected Files:**
  - `docker-compose.yml` (line 40)
  - `backend/src/main/resources/application.properties` (line 11)
  - `backend/src/main/java/com/hospital/backend/security/JwtService.java` (line 23)
- **Evidence:**
  `docker-compose.yml`:
  ```yaml
  JWT_SECRET: ${JWT_SECRET:-404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
  ```
  `application.properties`:
  ```properties
  jwt.secret=${JWT_SECRET:change-me-to-a-secure-secret-key}
  ```
- **Impact:** Deployments without explicit environment variable overrides will sign tokens using predictable public keys, allowing forgery of arbitrary user sessions.
- **Safe Recommendation:** Remove default secret fallbacks. Fail application startup (`IllegalStateException`) if `JWT_SECRET` is not set or has fewer than 256 bits of entropy.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### SEC-05: Unauthenticated Public MongoDB Exposure
- **Risk ID:** `SEC-05`
- **Severity:** **HIGH** (CVSS 8.5)
- **Affected Files:**
  - `docker-compose.yml` (lines 11-14)
- **Evidence:**
  ```yaml
  ports:
    - "27017:27017"
  environment:
    MONGO_INITDB_DATABASE: hospital_management_db
  ```
  Missing `MONGO_INITDB_ROOT_USERNAME` and `MONGO_INITDB_ROOT_PASSWORD`.
- **Impact:** Host-level unauthenticated read/write/drop access to clinical records.
- **Safe Recommendation:** Bind MongoDB port only to internal Docker bridge network (`expose` instead of `ports`), or mandate root authentication in development and production stacks.
- **Suggested Future Feature:** Feature 01.2 (Infrastructure & Environment Hardening).

---

### SEC-06: Broken Function-Level Authorization Locking Out Patients
- **Risk ID:** `SEC-06`
- **Severity:** **HIGH** (CVSS 7.5)
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/config/SecurityConfig.java` (lines 44-46)
  - `backend/src/main/java/com/hospital/backend/controller/PatientController.java` (lines 48, 54, 59)
- **Evidence:**
  `SecurityConfig.java`:
  ```java
  .requestMatchers(HttpMethod.GET, "/api/patients").hasAnyRole(ADMIN, DOCTOR)
  .requestMatchers("/api/patients/**").hasRole(ADMIN)
  ```
  Because `/api/patients/**` requires `ADMIN`, endpoints intended for patients (`/api/patients/me`, `/api/patients/{id}/history`, `/api/patients/{id}/family`) reject `PATIENT` tokens with HTTP 403 Forbidden.
- **Impact:** Patient portal core pages (medical history, family members, profile) fail with access denied.
- **Safe Recommendation:** Refine ant matchers to explicitly permit `ROLE_PATIENT` on `/api/patients/me`, `/api/patients/{id}/history`, and `/api/patients/{id}/family` with proper subject ownership validation.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### SEC-07: Incomplete Token Lifecycle (Non-Rotation, No Revocation)
- **Risk ID:** `SEC-07`
- **Severity:** **HIGH** (CVSS 7.2)
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/controller/AuthController.java` (lines 66-80)
  - `backend/src/main/java/com/hospital/backend/security/JwtService.java` (lines 50-53)
  - `backend/src/main/java/com/hospital/backend/scheduler/TokenCleanupScheduler.java` (lines 26-35)
- **Evidence:**
  `AuthController.refresh` returns the identical refresh token. Logout removes the refresh token in MongoDB, but does not invalidate or blacklist the 24-hour JWT access token.
- **Impact:** Leaked access tokens remain valid until expiration regardless of user logout.
- **Safe Recommendation:** Implement refresh token rotation (issue new refresh token on every refresh, invalidate old). Implement Redis-based or memory-based token blacklist or shorten access token lifespan to 15 minutes. Implement scheduled token expiration purge.
- **Suggested Future Feature:** Feature 01.1 (Security & Access Control Hardening).

---

### ARCH-01: Lack of Distributed Concurrency Control
- **Risk ID:** `ARCH-01`
- **Severity:** **HIGH**
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/service/impl/AppointmentServiceImpl.java` (line 35)
  - `backend/src/main/java/com/hospital/backend/service/impl/BedServiceImpl.java` (lines 54-69)
  - `backend/src/main/java/com/hospital/backend/model/Bed.java`
  - `backend/src/main/java/com/hospital/backend/model/Appointment.java`
- **Evidence:**
  `AppointmentServiceImpl.createAppointment` uses Java `synchronized` keyword, which only coordinates threads in a single JVM process and fails across horizontal instances. Neither `Bed` nor `Appointment` has `@Version` or MongoDB unique compound indexes.
- **Impact:** Double-booking of doctors and beds during concurrent patient requests.
- **Safe Recommendation:** Introduce compound unique index `(doctorId, appointmentDate, appointmentTime)` on appointments and atomic conditional updates (`findAndModify`) on bed status.
- **Suggested Future Feature:** Feature 02.1 (Clinical Concurrency & Data Integrity).

---

### ARCH-02: Scalability Bottleneck via In-Memory Filtering of Database Dumps
- **Risk ID:** `ARCH-02`
- **Severity:** **HIGH**
- **Affected Files:**
  - `backend/src/main/java/com/hospital/backend/service/impl/SearchServiceImpl.java` (lines 34, 145, 277)
  - `frontend/app/admin/reports/page.jsx` (lines 52-60)
- **Evidence:**
  `SearchServiceImpl` queries `userRepository.findAll()` and `prescriptionRepository.findAll()`, streaming all records into JVM heap to filter and paginate in Java memory. `ReportsPage.jsx` downloads entire databases to client browsers to compute statistics.
- **Impact:** Out of memory crashes and excessive latency as dataset grows beyond a few hundred records.
- **Safe Recommendation:** Replace in-memory streams with MongoDB Spring Data Query DSL, indexed repository criteria, and MongoDB Aggregation Pipelines.
- **Suggested Future Feature:** Feature 02.2 (Query Optimization & Aggregation Pipelines).
