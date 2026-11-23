# 🏥 Hospital Management System — Enterprise REST API Documentation

This document describes the REST API endpoints provided by the Hospital Management System backend.

- **Base URL:** `http://localhost:8080/api`
- **Authentication Scheme:** HTTP Bearer JWT (`Authorization: Bearer <token>`)
- **Health Check Endpoint:** `GET /api/health`

---

## 🔐 1. Authentication & Session (`/api/auth`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticates user & issues access + refresh tokens |
| `POST` | `/api/auth/register` | Public | Self-registration for new patients |
| `POST` | `/api/auth/refresh` | Public | Rotates expired access token using refresh token |
| `POST` | `/api/auth/logout` | Authenticated | Revokes current user refresh token & session |
| `POST` | `/api/auth/forgot-password` | Public | Generates and sends OTP / reset link via email |
| `POST` | `/api/auth/reset-password` | Public | Validates reset token and sets new password |

---

## 🩺 2. Appointments Management (`/api/appointments`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/appointments` | Patient / Admin | Schedules a new appointment slot |
| `GET` | `/api/appointments` | Admin / Doctor | Fetches all appointment records |
| `GET` | `/api/appointments/{id}` | Authenticated | Retrieves appointment details by ID |
| `GET` | `/api/appointments/patient/{patientId}` | Patient / Admin | Retrieves appointment history for a patient |
| `GET` | `/api/appointments/doctor/{doctorId}` | Doctor / Admin | Retrieves scheduled appointments for a doctor |
| `PUT` | `/api/appointments/{id}/status` | Doctor / Admin | Updates status (CONFIRMED, CANCELLED, COMPLETED) |

---

## 💊 3. Prescriptions Management (`/api/prescriptions`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/prescriptions` | Doctor | Issues medical prescription with medicine dosage |
| `GET` | `/api/prescriptions/patient/{patientId}` | Patient / Doctor | Fetches all prescriptions for a patient |
| `GET` | `/api/prescriptions/{id}/pdf` | Authenticated | Generates and downloads official PDF prescription |

---

## 👨‍⚕️ 4. Doctors & Availability (`/api/doctors`, `/api/availability`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/doctors` | All | Returns public doctor directory & specializations |
| `POST` | `/api/doctors` | Admin | Onboards a new doctor profile into the system |
| `PUT` | `/api/doctors/{id}` | Admin | Updates doctor specialization and contact details |
| `DELETE` | `/api/doctors/{id}` | Admin | Soft-deletes or archives doctor record |
| `GET` | `/api/availability/{doctorId}` | All | Fetches weekly time slots and availability status |
| `POST` | `/api/availability` | Doctor / Admin | Sets or updates doctor working schedule |

---

## 🏥 5. Hospital Facilities & Operations

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/beds` | Admin / Staff | Returns real-time hospital bed occupancy matrix |
| `POST` | `/api/beds/book` | Admin / Staff | Allocates a bed to an admitted patient |
| `GET` | `/api/invoices` | Admin / Staff | Fetches patient billing and payment invoices |
| `GET` | `/api/inventory` | Admin / Staff | Real-time pharmaceutical & medical supply levels |

---

## 📊 6. Analytics & Dashboards (`/api/dashboard`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/dashboard/summary` | Admin | Hospital KPI metrics, revenue, patient trends |
| `GET` | `/api/patient/dashboard/summary` | Patient | Patient portal overview (upcoming appointments, prescriptions) |

---

## ⚡ 7. Real-Time WebSockets (`/ws`)

- **STOMP Broker Endpoint:** `/ws`
- **User Destination:** `/user/queue/notifications`
- **Topic Channels:** `/topic/appointments`, `/topic/broadcasts`

---

## 📋 8. Clinical Records & Encounters (`/api/clinical`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/clinical/encounters` | Doctor / Admin | Initiates or resumes a clinical encounter from an appointment |
| `GET` | `/api/clinical/encounters/{id}` | Authenticated | Retrieves encounter details (finalized for patients, draft for clinicians) |
| `GET` | `/api/clinical/patients/{id}/encounters` | Patient / Doctor / Admin | Lists encounters for patient (finalized only for patient) |
| `PUT` | `/api/clinical/encounters/{id}/draft` | Doctor / Admin | Updates draft encounter narrative & chief complaint |
| `PUT` | `/api/clinical/encounters/{id}/soap` | Doctor / Admin | Saves draft SOAP consultation notes |
| `POST` | `/api/clinical/encounters/{id}/sign` | Doctor | Signs and finalizes encounter (makes note immutable) |
| `POST` | `/api/clinical/encounters/{id}/amend` | Doctor | Appends a traceable clinical addendum with justification |
| `POST` | `/api/clinical/encounters/{id}/vitals` | Doctor / Admin | Records vital sign observations within plausible limits |
| `GET` | `/api/clinical/encounters/{id}/vitals` | Authenticated | Fetches vital signs recorded for an encounter |
| `GET` | `/api/clinical/patients/{id}/vitals` | Patient / Doctor / Admin | Fetches historical vital signs for a patient |
| `GET` | `/api/clinical/patients/{id}/profile` | Patient / Doctor / Admin | Retrieves clinical profile (allergies, medical history) |
| `PUT` | `/api/clinical/patients/{id}/profile` | Doctor / Admin | Updates clinician-verified medical profile and allergies |
| `GET` | `/api/clinical/patients/{id}/timeline` | Patient / Doctor / Admin | Unified chronological medical timeline (encounters, vitals, Rx, labs) |


---

## 🧪 9. Laboratory & Diagnostic Ordering (`/api/lab`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/lab/tests` | Authenticated | Lists all active laboratory catalog tests |
| `GET` | `/api/lab/tests/search` | Authenticated | Paged search of laboratory catalog by code/name |
| `GET` | `/api/lab/tests/{id}` | Authenticated | Retrieves laboratory test definition by ID |
| `POST` | `/api/lab/tests` | Admin | Creates a new test definition in the catalog |
| `PUT` | `/api/lab/tests/{id}` | Admin | Updates test parameters and reference ranges |
| `DELETE` | `/api/lab/tests/{id}` | Admin | Deactivates a test definition (blocks new orders) |
| `POST` | `/api/lab/orders` | Doctor / Admin | Places a diagnostic test order linked to patient and encounter |
| `GET` | `/api/lab/orders` | Authenticated | Retrieves paged laboratory orders queue (filtered by user role) |
| `GET` | `/api/lab/orders/{id}` | Authenticated | Retrieves order details, specimens, and released results |
| `POST` | `/api/lab/orders/{id}/cancel` | Doctor / Admin | Cancels an order before results are released |
| `GET` | `/api/lab/patients/{patientId}/orders` | Authenticated | Fetches laboratory orders for a patient |
| `POST` | `/api/lab/orders/{orderId}/specimens` | Lab Tech / Doctor / Admin | Records specimen collection with unique accession number |
| `GET` | `/api/lab/orders/{orderId}/specimens` | Authenticated | Retrieves all specimens collected for an order |
| `PUT` | `/api/lab/specimens/{id}/receive` | Lab Tech / Doctor / Admin | Records specimen intake/receipt at the laboratory |
| `PUT` | `/api/lab/specimens/{id}/reject` | Lab Tech / Doctor / Admin | Rejects compromised specimen with clinical justification |
| `POST` | `/api/lab/orders/{orderId}/results` | Lab Tech / Doctor / Admin | Enters structured quantitative/qualitative result or saves draft |
| `GET` | `/api/lab/orders/{orderId}/results` | Authenticated | Retrieves results for an order (released only for patients) |
| `PUT` | `/api/lab/results/{id}/draft` | Lab Tech / Doctor / Admin | Updates draft result before submission |
| `POST` | `/api/lab/results/{id}/verify` | Doctor / Admin | Clinician verifies validity of entered laboratory findings |
| `POST` | `/api/lab/results/{id}/release` | Doctor / Admin | Officially releases verified report to patient medical record |
| `POST` | `/api/lab/results/{id}/correct` | Doctor / Admin | Creates a traceable revision for previously released result |
| `GET` | `/api/lab/results/{id}` | Authenticated | Retrieves result details by ID |
| `GET` | `/api/lab/patients/{patientId}/reports` | Patient / Doctor / Admin | Retrieves released laboratory reports for a patient |
| `GET` | `/api/lab/results/{id}/pdf` | Authenticated | Downloads official PDF laboratory report with security headers |
