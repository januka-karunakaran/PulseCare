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

---

## 💊 10. Pharmacy Inventory & Medication Dispensing (`/api/pharmacy`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/pharmacy/medicines` | Authenticated | Paged search of medicine catalog |
| `GET` | `/api/pharmacy/medicines/{id}` | Authenticated | Retrieves medicine details with total available stock |
| `GET` | `/api/pharmacy/medicines/low-stock` | Pharmacist / Admin | Lists catalog medicines with stock at or below reorder level |
| `POST` | `/api/pharmacy/medicines` | Pharmacist / Admin | Creates a new medicine entry in the catalog |
| `PUT` | `/api/pharmacy/medicines/{id}` | Pharmacist / Admin | Updates medicine metadata and reorder threshold |
| `DELETE` | `/api/pharmacy/medicines/{id}` | Pharmacist / Admin | Deactivates a medicine in the catalog |
| `POST` | `/api/pharmacy/inventory/receive` | Pharmacist / Admin | Receives a stock batch lot with expiration and storage location |
| `POST` | `/api/pharmacy/inventory/adjust` | Pharmacist / Admin | Adjusts batch available stock with audit reason code |
| `GET` | `/api/pharmacy/medicines/{id}/batches` | Pharmacist / Admin | Lists all active batches for a specific medicine |
| `GET` | `/api/pharmacy/batches/near-expiry` | Pharmacist / Admin | Identifies batches expiring within the specified days (default 30) |
| `GET` | `/api/pharmacy/medicines/{id}/movements` | Pharmacist / Admin | Returns append-only stock movement audit ledger |
| `GET` | `/api/pharmacy/queue` | Pharmacist / Admin | Retrieves pending prescription queue with stock availability status |
| `GET` | `/api/pharmacy/queue/{prescriptionId}` | Pharmacist / Admin | Returns prescription queue item with line-item stock readiness |
| `POST` | `/api/pharmacy/dispense` | Pharmacist / Admin | Atomically executes FEFO stock allocation and dispenses medication |
| `POST` | `/api/pharmacy/reverse` | Pharmacist / Admin | Reverses a dispensation and moves stock to physical quarantine |
| `GET` | `/api/pharmacy/dispense/{id}` | Authenticated | Retrieves details of a specific dispensation |
| `GET` | `/api/pharmacy/patients/{patientId}/dispenses` | Authenticated | Retrieves medication dispensation history for a patient |


---

## 11. Hospital Billing, Invoicing & Payment Integrity API

All monetary amounts are represented as decimal numbers with two fractional places (e.g., `150.00`). All endpoints require an active JWT bearer token.

### 11.1 Service Catalog Management
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/billing/services` | `ADMIN`, `BILLING_STAFF` | Create a new billable service catalog item |
| `PUT` | `/api/billing/services/{id}` | `ADMIN`, `BILLING_STAFF` | Update an existing catalog service tariff |
| `DELETE` | `/api/billing/services/{id}` | `ADMIN`, `BILLING_STAFF` | Deactivate a catalog service from future billing |
| `GET` | `/api/billing/services/{id}` | `ADMIN`, `BILLING_STAFF` | Get details of a single catalog service |
| `GET` | `/api/billing/services` | `ADMIN`, `BILLING_STAFF` | List catalog services with pagination & category filter |
| `GET` | `/api/billing/services/active` | `ADMIN`, `BILLING_STAFF`, `DOCTOR` | List all currently active catalog services |

### 11.2 Billable Charge Registry
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/billing/charges` | `ADMIN`, `BILLING_STAFF` | Register a manual billable charge with audit rationale |
| `GET` | `/api/billing/charges` | `ADMIN`, `BILLING_STAFF` | List uninvoiced charges for a patient |
| `GET` | `/api/billing/charges/patient/{patientId}` | `ADMIN`, `BILLING_STAFF` | Paginated charge ledger for a patient |

### 11.3 Invoicing & Statements
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/billing/invoices/draft` | `ADMIN`, `BILLING_STAFF` | Create draft invoice from uninvoiced charges |
| `POST` | `/api/billing/invoices/{id}/issue` | `ADMIN`, `BILLING_STAFF` | Finalize and issue invoice to patient |
| `POST` | `/api/billing/invoices/{id}/void` | `ADMIN`, `BILLING_STAFF` | Void unpaid invoice and release linked charges |
| `GET` | `/api/billing/invoices/{id}` | `ADMIN`, `BILLING_STAFF`, `PATIENT` (Own) | Retrieve full invoice details |
| `GET` | `/api/billing/invoices` | `ADMIN`, `BILLING_STAFF` | List invoices with status filtering |
| `GET` | `/api/billing/invoices/{id}/pdf` | `ADMIN`, `BILLING_STAFF`, `PATIENT` (Own) | Download official PDF invoice statement |

### 11.4 Payments & Collections
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/billing/invoices/{invoiceId}/payments` | `ADMIN`, `BILLING_STAFF` | Record payment against invoice with atomic concurrency |
| `GET` | `/api/billing/payments/{id}` | `ADMIN`, `BILLING_STAFF`, `PATIENT` (Own) | Retrieve payment transaction receipt |
| `GET` | `/api/billing/payments/{id}/receipt/pdf` | `ADMIN`, `BILLING_STAFF`, `PATIENT` (Own) | Download official PDF cashier receipt |
| `GET` | `/api/billing/invoices/{invoiceId}/payments` | `ADMIN`, `BILLING_STAFF`, `PATIENT` (Own) | List payments allocated to an invoice |
| `GET` | `/api/billing/invoices/{invoiceId}/ledger` | `ADMIN`, `BILLING_STAFF` | List audit ledger entries for an invoice |

### 11.5 Refunds & Credit Notes
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/billing/payments/{paymentId}/refund` | `ADMIN`, `BILLING_STAFF` | Process verified payment refund |
| `POST` | `/api/billing/credit-notes` | `ADMIN`, `BILLING_STAFF` | Issue credit note adjustment against an invoice |
| `GET` | `/api/billing/invoices/{invoiceId}/refunds` | `ADMIN`, `BILLING_STAFF` | List refunds issued on an invoice |
| `GET` | `/api/billing/invoices/{invoiceId}/credit-notes` | `ADMIN`, `BILLING_STAFF` | List credit notes applied to an invoice |

### 11.6 Patient Portal Self-Service
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/billing/my-invoices` | `PATIENT` | List authenticated patient's issued and paid invoices |
| `GET` | `/api/billing/my-payments` | `PATIENT` | List authenticated patient's payment receipts |

### 11.7 Reconciliation & Executive Reporting
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/billing/reports/summary` | `ADMIN`, `BILLING_STAFF` | Overall revenue KPIs, collections, outstanding balance |
| `GET` | `/api/billing/reconciliation` | `ADMIN`, `BILLING_STAFF` | Daily financial closing and payment method audit report |
