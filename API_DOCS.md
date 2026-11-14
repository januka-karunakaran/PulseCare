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
