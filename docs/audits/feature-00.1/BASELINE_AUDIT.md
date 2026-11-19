# ðŸ¥ PulseCare 2.0 â€” Existing System Baseline Audit Report

**Document ID:** `AUDIT-00.1-BASELINE`
**Target Repository:** `https://github.com/januka-karunakaran/PulseCare.git`
**Target Branch:** `main`
**Audited Commit SHA:** `be6be662c1b50e58d5fe77439af2ce14e4f4f655`
**Feature Branch:** `feature/00.1-baseline-audit`
**Audit Date:** November 2026
**Lead Auditor:** Senior Full-Stack Software Architect, Spring Boot & Next.js Engineer, Healthcare Application Security Reviewer, and QA Engineer

---

## 1. Executive Summary

PulseCare is envisioned as an enterprise clinical operating system and hospital management platform catering to administrators, doctors, and patients. Prior to commencing feature implementation for **PulseCare 2.0**, an exhaustive, evidence-based, read-only architectural, source code, security, database, and infrastructure baseline audit was conducted on commit `be6be662c1b50e58d5fe77439af2ce14e4f4f655`.

### Key Conclusions:
1. **Architecture Model:** Despite documentation claims of a "decoupled event-driven microservices architecture," the codebase is in reality a **classic two-tier decoupled monolithic application** (a Spring Boot monolithic REST backend and a Next.js frontend SPA/SSR client) sharing a single MongoDB document database.
2. **Fabricated / Phantom Features in Documentation:** Several capabilities highlighted prominently in the `README.md` and `CHANGELOG.md` do not exist in the source code. Notably:
   - Schedulers (`TokenCleanupScheduler.java`, `AppointmentReminderScheduler.java`) contain empty no-op methods that execute no queries and perform no cleanups or alerts.
   - Domain Event Bus (`AppointmentEventPublisher.java`, `AppointmentCreatedEvent.java`, `AppointmentCancelledEvent.java`) exists as dead codeâ€”the publisher is never injected or called anywhere in the codebase, and zero event listeners exist.
   - Optimistic concurrency control (`@Version`) and Spring Data Auditing (`@CreatedDate`, `@LastModifiedDate`) are completely absent from all 17 entity models despite explicit claims.
   - Actuator / Telemetry endpoint (`/api/health`) returns hardcoded JSON strings without probing MongoDB connectivity or memory telemetry.
3. **Critical Security Vulnerabilities:**
   - **Privilege Escalation:** Public user registration accepts an arbitrary `role` parameter (`PATIENT`, `DOCTOR`, `ADMIN`). The registration frontend explicitly provides an "Admin" role selection button, allowing anyone to register an administrator account directly.
   - **IDOR / Broken Object-Level Authorization (BOLA):** Endpoints under `/api/appointments/**` allow any authenticated `PATIENT` to view, modify, or delete appointments belonging to any patient or doctor. Prescriptions (`/api/prescriptions/patient/{id}`, `/api/prescriptions/{id}/pdf`) and billing invoices (`/api/invoices/patient/{id}`) allow any patient to inspect other patients' clinical data.
   - **CORS & WebSocket Wildcard Misconfiguration:** `CorsConfig.java` enables `setAllowCredentials(true)` alongside wildcard origin patterns (`*`). WebSocket SockJS endpoint `/ws/notifications` uses `setAllowedOriginPatterns("*")` without STOMP session authentication.
   - **Hardcoded & Default JWT Secret:** In `docker-compose.yml`, a fallback JWT secret (`404E635266...`) is checked into source control. In `application.properties`, a 30-character weak fallback is defined.
   - **Insecure MongoDB Exposure:** In `docker-compose.yml`, MongoDB 7.0 is exposed directly on host port `27017` with no authentication enabled.
4. **Broken Modules & Disconnects:**
   - **Medicine Reminders:** The frontend portal exposes a full reminder interface (`/patient/reminders`) calling `/api/reminders/**`, but no controller or service exists on the backend (404/403 runtime failure).
   - **WebSocket Transport Mismatch:** The frontend hook `useWebSocket.js` attempts to connect via `socket.io-client`, whereas the backend implements Spring STOMP over SockJS (`/ws/notifications`).
   - **Zero-Config Onboarding Blocker:** Out of the box, `app.mail.enabled` is false. Because user registration enforces email OTP verification, self-registration is completely unusable on default configurations. Furthermore, no database seeding exists for documented demo accounts (`admin@pulsecare.com`, etc.).

---

## 2. Audited Commit SHA and Environment

- **Repository:** `https://github.com/januka-karunakaran/PulseCare.git`
- **Starting Main Commit SHA:** `be6be662c1b50e58d5fe77439af2ce14e4f4f655`
- **Active Branch for Audit:** `feature/00.1-baseline-audit`
- **Host Platform:** Windows 11 (build 10.0.26100)
- **Local JDK:** OpenJDK 17.0.12 LTS
- **Local Maven:** 3.9.x via `./mvnw.cmd` wrapper
- **Local Node.js / NPM:** Node.js v22.x / npm 10.x
- **Container Engine:** Docker Compose v2.x

---

## 3. Actual Technology Stack

| Layer | Documented Specification | Actual Implemented Stack | Discrepancies & Notes |
|---|---|---|---|
| **Backend Framework** | Spring Boot 3.5.6 | Spring Boot 3.5.6 (Java 17) | Matches `pom.xml` |
| **Data Persistence** | MongoDB 8.x Enterprise | MongoDB 7.0 via Docker / Local | `docker-compose.yml` specifies `mongo:7.0`. No 8.x enterprise features used |
| **Security** | Spring Security 6, Stateless HMAC-512 JWT | Spring Security 6.x, `jjwt-api` 0.12.6, HMAC-SHA256 | Uses `Keys.hmacShaKeyFor` with raw UTF-8 string bytes (32-byte pad). Not HMAC-512 |
| **Frontend Framework**| Next.js 16.2.4 (Turbopack), React 19.2.4 | Next.js 16.2.4 (App Router), React 19.2.4 | Matches `package.json` |
| **Styling & Icons** | Tailwind CSS 4, React Icons 5.6 | Tailwind CSS 4.2.4, `@tailwindcss/postcss`, React Icons | Uses PostCSS v8 integration |
| **Charts & Reporting**| Recharts 3.8.1, SheetJS (XLSX) | Recharts 3.8.1, XLSX 0.18.5, jspdf 4.2.1, html2canvas | Client-side aggregation and export |
| **Real-Time Layer** | SockJS + STOMP Listener | Spring STOMP (`/ws/notifications`) vs `socket.io-client` & `@stomp/stompjs` | Conflict between `useWebSocket.js` (Socket.IO) and `NotificationListener.jsx` (STOMP) |
| **Document Generation**| Apache PDFBox Digital Rx Engine | Apache PDFBox 2.0.29 | Synchronous single-page PDF rendering |
| **CI / CD** | GitHub Actions Pipeline (`ci.yml`) | Two competing workflows: `.github/workflows/ci.yml` and `ci-cd.yml` | `ci.yml` ignores ESLint errors (`continue-on-error: true`) and lacks test runs for frontend |

---

## 4. Architecture Assessment

### 4.1 Deployment Topography
The system is structured as two discrete subprojects:
- `backend/`: Single runnable Spring Boot application JAR containing 16 REST controllers, 17 service implementations, 17 Spring Data repositories, and schedulers.
- `frontend/`: Single Next.js standalone application running on Node.js port 3000.

**Determination:** PulseCare is a **two-tier modular monolith** communicating over REST HTTP and WebSocket STOMP. It is not an event-driven microservices architecture. Claims of microservices in promotional headings are inaccurate.

### 4.2 Module Boundaries & Decoupling
- **Coupling:** Domain services are directly coupled via Spring DI. For instance, `AppointmentServiceImpl` directly injects `DoctorRepository`, `PatientRepository`, `UserRepository`, `AvailabilityService`, and `NotificationService`.
- **Event Bus:** `AppointmentEventPublisher` exists under `backend/src/main/java/com/hospital/backend/event/` with events `AppointmentCreatedEvent` and `AppointmentCancelledEvent`, but is **never invoked**. All notifications and status updates are executed synchronously within request execution paths.
- **Transactions:** The backend has no `@Transactional` annotations across MongoDB service methods. Multi-document operations (e.g. creating user + creating doctor/patient profile, booking bed + updating bed status) run without replica set multi-document ACID transactions.

---

## 5. Backend Source & Configuration Audit

### 5.1 Controllers and Endpoints (16 Total)
1. **`AdminController` (`/api/admin`):** Implements staff management, doctor approval, and audit logs. Protected with `@PreAuthorize("hasRole('ADMIN')")`.
2. **`AppointmentController` (`/api/appointments`):** Implements booking, listing, status updates, and cancellation. Lacks method-level authorization; open to all roles.
3. **`AuthController` (`/api/auth`):** Implements login, signup, OTP dispatch, password reset, token refresh, and logout. Signup exposes privilege escalation.
4. **`AvailabilityController` (`/api/availability`):** Implements doctor schedule management. Read endpoints open to all roles; modifications restricted to `DOCTOR`.
5. **`BedController` (`/api/beds`):** Implements bed creation, listing, booking, and discharge. Lacks optimistic concurrency control.
6. **`DashboardController` (`/api/dashboard`):** Admin analytics summary, revenue, and daily metrics. Has `@CrossOrigin(origins = "*")`.
7. **`DoctorController` (`/api/doctors`):** Staff directory, onboarding, updates, and deletion. In `SecurityConfig`, `GET /api/doctors` is restricted to authenticated users, contradicting `API_DOCS.md` which lists it as Public.
8. **`HealthController` (`/api/health`):** Dummy health probe returning static JSON without database verification.
9. **`InventoryController` (`/api/inventory`):** Medical supply inventory tracking and stock adjustments.
10. **`InvoiceController` (`/api/invoices`):** Billing invoices and payments. Lacks ownership validation for patient invoices.
11. **`NotificationController` (`/api/notifications`):** In-app user notifications. Has `@CrossOrigin(origins = "*")`.
12. **`PatientController` (`/api/patients`):** Patient records, history, and family members. Route `/api/patients/**` is locked to `ADMIN` in `SecurityConfig`, blocking patients from `/api/patients/me`.
13. **`PatientDashboardController` (`/api/patient/dashboard`):** Patient-specific metrics, upcoming appointments, and vitals.
14. **`PrescriptionController` (`/api/prescriptions`):** Prescription issuance, listing, and PDF downloads. Lacks patient record-level authorization.
15. **`SearchController` (`/api/search`):** Search for doctors, patients, appointments, and prescriptions. Executes in-memory filtering over `findAll()`, posing severe scalability risks.
16. **`UserController` (`/api/users`):** User profile retrieval and updates. Allows any user to inspect another user's profile via `GET /api/users/{userId}`.

### 5.2 Schedulers & Background Tasks
- `TokenCleanupScheduler.java`: Has `@Scheduled(cron = "0 0 0 * * ?")`, but contains zero deletion logic.
- `AppointmentReminderScheduler.java`: Has `@Scheduled(cron = "0 0 * * * ?")`, but only logs a debug string without querying appointments or dispatching notifications.

---

## 6. Authentication & Security Audit

### 6.1 Vulnerability Deep Dive

#### Risk A: Fallback JWT Secret in Docker Compose & Configurations
- **File:** `docker-compose.yml:40`, `backend/src/main/resources/application.properties:11`
- **Finding:** Hardcoded string `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` is used as a fallback default. In `application.properties`, fallback is `change-me-to-a-secure-secret-key`.
- **Impact:** Anyone running the stack without setting `JWT_SECRET` has tokens signed with known public secrets, enabling token forgery.

#### Risk B: MongoDB Unauthenticated Host Exposure
- **File:** `docker-compose.yml:11-14`
- **Finding:** Port `27017:27017` is bound directly to the host without credentials configured in `docker-compose.yml` (`MONGO_INITDB_ROOT_USERNAME` is missing).
- **Impact:** Any process or network actor on the host network can access the database unauthenticated.

#### Risk C: Permissive CORS & Wildcard WebSocket Origins
- **File:** `backend/src/main/java/com/hospital/backend/config/CorsConfig.java:22-29`, `backend/src/main/java/com/hospital/backend/config/WebSocketConfig.java:26`
- **Finding:** `CorsConfig` sets `setAllowCredentials(true)` combined with `setAllowedOriginPatterns(List.of("*"))`. `WebSocketConfig` sets `.setAllowedOriginPatterns("*")`.
- **Impact:** Credentialed cross-origin requests from arbitrary third-party origins are accepted, creating Cross-Origin Request Forgery and data exfiltration vectors.

#### Risk D: Broken Object-Level Authorization (IDOR)
- **File:** `backend/src/main/java/com/hospital/backend/config/SecurityConfig.java:48-56`, `backend/src/main/java/com/hospital/backend/controller/AppointmentController.java:39-42`, `backend/src/main/java/com/hospital/backend/controller/PrescriptionController.java:44-47,60-67`
- **Finding:** `SecurityConfig` grants `ROLE_PATIENT` global access to `/api/appointments/**` and `/api/prescriptions/**`. Neither the controllers nor the services verify whether `patientId` matches the authenticated subject.
- **Impact:** A patient can view another patient's medical records, download another patient's prescription PDF, cancel another patient's appointment, or view all appointments in the hospital.

#### Risk E: Insecure Registration Role Selection
- **File:** `backend/src/main/java/com/hospital/backend/dto/SignupRequest.java:22`, `backend/src/main/java/com/hospital/backend/service/impl/AuthServiceImpl.java:93`, `frontend/app/register/page.jsx:22-26`
- **Finding:** `SignupRequest` accepts an arbitrary `role` string, which `AuthServiceImpl` assigns directly to the user. The registration UI renders a button for `ADMIN`.
- **Impact:** Complete system takeover by self-registering as `ADMIN`.

#### Risk F: Refresh Token Non-Rotation and Lack of Blacklist
- **File:** `backend/src/main/java/com/hospital/backend/controller/AuthController.java:78`, `backend/src/main/java/com/hospital/backend/security/JwtService.java:50-53`
- **Finding:** `/api/auth/refresh` returns the original refresh token without rotation. `JwtService.isTokenValid` checks only expiration and username; no blacklist/revocation check exists.
- **Impact:** Stolen access tokens cannot be revoked before the 24-hour expiration window.

---

## 7. Frontend Source Audit

### 7.1 Architecture & Routing
The frontend utilizes Next.js 16 App Router with React 19. Pages are divided into role workspaces:
- `/admin/*`: Command center, beds, billing, staff, appointments, prescriptions, reports.
- `/doctor/*`: Clinical dashboard, appointments, availability, prescriptions.
- `/patient/*`: Portal, booking, history, family, reminders.
- `/login`, `/register`, `/forgot-password`, `/reset-password`: Authentication pages.

### 7.2 Discrepancies & Deficiencies
1. **Mock & Broken Integrations:**
   - `/patient/reminders`: Calls non-existent `/api/reminders` endpoint.
   - `/admin/reports`: Employs heavy in-browser aggregation by executing 6 parallel `findAll` queries across doctors, appointments, patients, prescriptions, and beds.
2. **Middleware Pass-Through:**
   - `frontend/app/middleware.js` returns `NextResponse.next()` without token verification.
3. **Storage Security:**
   - Tokens and roles are stored in browser `localStorage`, susceptible to XSS.
4. **WebSocket Library Clash:**
   - `frontend/hooks/useWebSocket.js` relies on `socket.io-client`.
   - `frontend/app/components/NotificationListener.jsx` relies on `@stomp/stompjs` + `sockjs-client`.

---

## 8. Database & Model Audit

### 8.1 MongoDB Document Models (17 Models)
1. `User` (collection: `users`): Contains credentials, profile, and role.
2. `Doctor` (collection: `doctors`): Doctor metadata, ratings, and schedule.
3. `Patient` (collection: `patients`): Demographics, blood group, embedded `familyMembers`.
4. `Appointment` (collection: `appointments`): Booking slot, token number, status.
5. `Prescription` (collection: `prescriptions`): Diagnosis, medicines list, notes.
6. `Bed` (collection: `beds`): Bed number, type, ward, status, price.
7. `BedBooking` (collection: `bed_bookings`): Admission and discharge tracking.
8. `Invoice` (collection: `invoices`): Billing items, subtotal, tax, payment status.
9. `InventoryItem` (collection: `inventory`): Medical supplies and stock quantities.
10. `Notification` (collection: `notifications`): In-app alerts and read flags.
11. `Availability` (collection: `doctor_availability`): Working days and time slots.
12. `AuditLog` (collection: `audit_logs`): Action logging.
13. `EmailVerificationCode` (collection: `email_verification_codes`): 6-digit OTP hashes.
14. `PasswordResetToken` (collection: `password_reset_tokens`): UUID hashes for password resets.
15. `RefreshToken` (collection: `refresh_tokens`): Refresh token store.
16. `LabResult` (collection: `lab_results`): Test results and units.
17. `MedicineReminder` (collection: `medicine_reminders`): Model exists without controller.

### 8.2 Indexes & Integrity
- **Indexed Fields:** Only `EmailVerificationCode` (`email` unique, `expiresAt` TTL) and `PasswordResetToken` (`tokenHash` unique, `expiresAt` TTL) declare Spring Data index annotations.
- **Missing Critical Indexes:**
  - `User.email`: Missing `@Indexed(unique = true)`. Uniqueness is enforced only at application level.
  - `Appointment`: Missing compound index on `(doctorId, appointmentDate, appointmentTime)`.
  - `Bed.bedNumber`: Missing unique constraint index.
  - `RefreshToken.token`: Missing index.
- **Lack of Auditing Fields:** Zero models use `@CreatedDate` or `@LastModifiedDate`.
- **Lack of Concurrency Control:** Zero models use `@Version`.

---

## 9. CI/CD & DevOps Review

1. **Duplicate Workflows:**
   - `.github/workflows/ci.yml`: Triggers on `main`, `develop`. Runs backend test and package, frontend `npm ci`, `npm run lint` (with `continue-on-error: true`), and `npm run build`.
   - `.github/workflows/ci-cd.yml`: Triggers on `main`, `Januka`. Runs backend test, frontend lint (with `continue-on-error: true`), build, and pushes Docker images to GHCR.
2. **Missing Test Gates:**
   - Neither CI workflow executes frontend unit tests (`npm test` is not called).
3. **Deprecated Docker Compose Syntax:**
   - Both compose files use `version: '3.8'`, which produces obsolete attribute warnings in modern Docker Compose v2.

---

## 10. Overall Readiness Assessment

| Evaluation Dimension | Rating | Verdict |
|---|---|---|
| **Architecture Modernity** | Medium | Sound two-tier foundation, but misleading marketing claims |
| **Code Cleanliness & Structure** | High | Clean DDD folder layout, consistent DTO envelopes |
| **Clinical Security & HIPAA / GDPR**| Critical / Fails | Severe IDOR, unauthenticated Mongo, role escalation |
| **Concurrency & Data Safety** | Low | Absence of atomic slot reservation, no `@Version` |
| **Automated Test Coverage** | Low | Only 6 backend tests, missing frontend test script |
| **Production Readiness** | NOT READY | Requires immediate remediation of P0 security & data flaws |
