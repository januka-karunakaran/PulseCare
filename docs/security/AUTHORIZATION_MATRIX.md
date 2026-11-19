# PulseCare 2.0 — Endpoint-Level Authorization Matrix

**Feature:** 01.1 Security & Access Control Hardening (Follow-up 01.1.10)
**Target Specification:** Feature 01.1.2, 01.1.3, 01.1.9 & 01.1.10
**Audited Baseline Reference:** `docs/audits/feature-00.1/BASELINE_AUDIT.md` (SEC-01, SEC-06, SEC-07)
**Standard:** OWASP Top 10 API Security (BOLA/IDOR & BFLA Prevention) / HIPAA Security Rule

---

## 1. Overview & Authorization Architecture

PulseCare 2.0 enforces a multi-tier defense-in-depth authorization model:
1. **Network & Filter Level (`SecurityConfig`):** Ant matchers validate HTTP method, URL pattern, and authenticated role (`ADMIN`, `DOCTOR`, `PATIENT`).
2. **Object & Function Level (`@PreAuthorize` & `AuthorizationService`):** Fine-grained SpEL and programmatic service checks confirm resource ownership (`isPatientOwner`, `isDoctorOwner`) and clinical relationships (`hasDoctorPatientRelationship`).
3. **Identity Derivation:** The authenticated identity is strictly derived from the verified Spring Security principal (`user.getId()` and associated `Patient` / `Doctor` entity IDs). Identifier values supplied in request bodies or query parameters are never trusted as proof of authorization. Email addresses are never used as independent proof of medical record ownership.

---

## 2. Comprehensive Endpoint Authorization Matrix

| Method | Endpoint Path | Permitted Roles | Ownership & Relationship Check | Remediation Detail & Security Guard | Status |
|---|---|---|---|---|---|
| `POST` | `/api/auth/signup` | Public / Anonymous | Forced to `Role.PATIENT` | Non-patient role requests rejected with HTTP 400 (`SEC-02`) | Remediated |
| `POST` | `/api/auth/login` | Public / Anonymous | Credentials verified | Validates active status and returns signed JWT + initial refresh token | Remediated |
| `POST` | `/api/auth/refresh` | Authenticated / Bearer | Atomic token consumption | MongoDB-enforced atomic `findAndRemove` with unique index & SHA-256 hash (01.1.10) | Remediated |
| `POST` | `/api/auth/logout` | Authenticated | Current User | Invalidates refresh tokens & sets `tokenInvalidBefore` timestamp (`SEC-07`, 01.1.9) | Remediated |
| `GET` | `/api/patients/me` | `PATIENT`, `DOCTOR` | Current authenticated user | Returns patient profile for logged-in user; fixed 403 lock-out (`SEC-06`) | Remediated |
| `GET` | `/api/patients` | `ADMIN`, `DOCTOR` | Role check | Restricted to administrative and clinical staff | Remediated |
| `GET` | `/api/patients/{id}` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#id)` | Patients can only view own record; doctors require clinical link or admin | Remediated |
| `PUT` | `/api/patients/{id}` | `ADMIN`, `PATIENT` | `@authz.isPatientOwner(#id)` | Patients can only update own profile; admins can update all | Remediated |
| `DELETE` | `/api/patients/{id}` | `ADMIN` | Role check | Restricted to administrators | Remediated |
| `GET` | `/api/patients/{id}/history` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#id)` | Patients can only view own medical history (`SEC-01`, `SEC-06`) | Remediated |
| `GET` | `/api/patients/{id}/family` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#id)` | Patients can only view own family members (`SEC-01`, `SEC-06`) | Remediated |
| `POST` | `/api/appointments` | `ADMIN`, `DOCTOR`, `PATIENT` | Ownership validation | Patients can only book appointments for their own patient profile | Remediated |
| `GET` | `/api/appointments` | `ADMIN`, `DOCTOR`, `PATIENT` | Role-based dataset filtering | Dual-identity resolution: patients see all appointments matching profile or user ID (01.1.10) | Remediated |
| `GET` | `/api/appointments/{id}` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.checkAppointmentAccess` | IDOR blocked: patient can only read appointment if `patientId` matches (`SEC-01`) | Remediated |
| `GET` | `/api/appointments/patient/{patientId}` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#patientId)` | IDOR blocked: cross-patient appointment list queries return 403 | Remediated |
| `GET` | `/api/appointments/doctor/{doctorId}` | `ADMIN`, `DOCTOR` | `@authz.isDoctorOwner(#doctorId)` | Doctors can query schedule via profile ID or user ID; others denied (01.1.9) | Remediated |
| `PUT` | `/api/appointments/{id}/status` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.canUpdateAppointmentStatus` | Patients can ONLY cancel (`CANCELLED`) their own appointment; cannot confirm or complete | Remediated |
| `DELETE` | `/api/appointments/{id}` | `ADMIN`, `PATIENT` | `authz.checkAppointmentAccess` | Patients can only delete/cancel their own uncompleted appointments | Remediated |
| `POST` | `/api/prescriptions` | `ADMIN`, `DOCTOR` | Clinical validation | Patients forbidden from issuing prescriptions | Remediated |
| `GET` | `/api/prescriptions/{id}` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.checkPrescriptionAccess` | IDOR blocked: patients can only access prescriptions issued to them (`SEC-01`) | Remediated |
| `GET` | `/api/prescriptions/{id}/pdf` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.checkPrescriptionAccess` | IDOR blocked: patients cannot download arbitrary prescription PDFs (`SEC-01`) | Remediated |
| `GET` | `/api/prescriptions/patient/{patientId}` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#patientId)` | IDOR blocked: patients cannot list other patients' prescriptions | Remediated |
| `GET` | `/api/prescriptions/doctor/{doctorId}` | `ADMIN`, `DOCTOR` | `@authz.isDoctorOwner(#doctorId)` | Doctors can query prescriptions via profile ID or user ID (01.1.9) | Remediated |
| `GET` | `/api/invoices/patient/{patientId}` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#patientId)` | IDOR blocked: cross-patient invoice queries return 403 (`SEC-01`) | Remediated |
| `GET` | `/api/invoices/{id}` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.checkInvoiceAccess` | IDOR blocked: patient can only view invoices linked to their patient ID | Remediated |
| `GET` | `/api/beds/patient/{patientId}` | `ADMIN`, `DOCTOR`, `PATIENT` | `@authz.canAccessPatient(#patientId)` | IDOR blocked: bed allocation by patient ID requires ownership | Remediated |
| `GET` | `/api/users/{userId}` | `ADMIN`, `DOCTOR`, `PATIENT` | `authz.isUserOwner(#userId)` | Users can only inspect their own account profile unless `ADMIN` | Remediated |
| `GET` | `/api/search/patients` | `ADMIN`, `DOCTOR`, `PATIENT` | Auto-scoping filter | Non-admins/non-doctors forced to filter strictly by their own ID | Remediated |
| `PUT` | `/api/notifications/{id}/read` | `ADMIN`, `DOCTOR`, `PATIENT` | Ownership validation | Users cannot mark other users' notifications as read | Remediated |
| `SUBSCRIBE` | `/topic/notifications/{userId}` | `ADMIN`, `DOCTOR`, `PATIENT` | ChannelInterceptor JWT check | WebSocket subscription blocked unless user subscribes to their own destination (`SEC-03`) | Remediated |

---

## 3. Identity Verification & Clinical Relationship Logic

In `AuthorizationService.java`:
- **Dual Identity Resolution (`isPatientOwner`):**
  - Resolves both `Patient.id` (Patient profile document key) and `Patient.userId` (User account key).
  - Eliminates reliance on matching email addresses as proof of medical ownership.
- **Doctor Ownership Mapping (`isDoctorOwner`):**
  - Resolves both `Doctor.id` and `Doctor.userId`.
- **Fail-Closed Default:**
  - If a user is unauthenticated or if relationship checks fail, authorization defaults to false and throws `AccessDeniedException` (HTTP 403).
