# ðŸ“Š PulseCare 2.0 â€” Feature & Module Audit Matrix

**Audited Commit:** `be6be662c1b50e58d5fe77439af2ce14e4f4f655`
**Classification Key:**
- **IMPLEMENTED:** Fully realized in both backend and frontend with functional end-to-end flow.
- **PARTIAL:** Implemented with missing checks, unhandled edge cases, or broken sub-features.
- **MISSING:** Documented or UI-represented feature with no backing code.
- **BROKEN:** Implemented but fails at runtime due to protocol mismatches, 404/403 errors, or fatal crashes.
- **UNVERIFIED:** Cannot be fully proven without isolated live hardware or third-party gateways (e.g., SMTP delivery).

---

## Comprehensive Feature Matrix

| Module / Domain | Specific Functionality | Backend Implementation & Evidence | Frontend Implementation & Evidence | Status | Automated Tests | Missing Work / Gaps |
|---|---|---|---|---|---|---|
| **Authentication** | User Login | `AuthController.java:49`, `AuthServiceImpl.java:151` | `login/page.jsx`, `authService.js:5` | **IMPLEMENTED** | `AuthServiceTest.java` (Mock) | Stolen token revocation; access tokens valid 24h even after logout. |
| **Authentication** | User Self-Registration | `AuthController.java:38`, `AuthServiceImpl.java:67` | `register/page.jsx`, `authService.js:23` | **BROKEN** | None | Fails out-of-the-box because OTP email fails if SMTP is unconfigured. Allows `role: ADMIN` privilege escalation. |
| **Authentication** | Email OTP Verification | `AuthController.java:42`, `AuthServiceImpl.java:125` | `register/page.jsx:47`, `authService.js:83` | **PARTIAL** | None | No sandbox email preview; blocks signup if SMTP credentials not provided. |
| **Authentication** | Token Refresh | `AuthController.java:65`, `RefreshTokenServiceImpl.java:36` | `services/api.js:15`, `authService.js:48` | **PARTIAL** | None | Refresh token is NOT rotated; same refresh token returned repeatedly. |
| **Authentication** | User Logout | `AuthController.java:83`, `RefreshTokenServiceImpl.java:44` | `utils/auth.js:27`, `authService.js:66` | **PARTIAL** | None | Deletes refresh token in DB, but does NOT invalidate or blacklist the active JWT access token. |
| **Authentication** | Forgot Password | `AuthController.java:53`, `AuthServiceImpl.java:175` | `forgot-password/page.jsx` | **PARTIAL** | None | Dependent on external SMTP. Unverified email delivery without SMTP configuration. |
| **Authentication** | Reset Password | `AuthController.java:59`, `AuthServiceImpl.java:201` | `reset-password/page.jsx` | **IMPLEMENTED** | None | Token validation and password update works once token is obtained. |
| **Appointments** | Book Appointment | `AppointmentController.java:20`, `AppointmentServiceImpl.java:35` | `app/page.jsx` (`#booking`), `patient/appointments/page.jsx` | **PARTIAL** | None | Uses JVM `synchronized`; no MongoDB unique compound index; double-booking possible across multiple instances. |
| **Appointments** | List All Appointments | `AppointmentController.java:24`, `AppointmentServiceImpl.java:105` | `admin/appointments/page.jsx` | **PARTIAL** | None | `SecurityConfig` permits `ROLE_PATIENT` to view all hospital appointments. |
| **Appointments** | Patient Appointments | `AppointmentController.java:39`, `AppointmentServiceImpl.java:124` | `patient/my-appointments/page.jsx` | **BROKEN** | `AppointmentServiceTest.java` | Broken Object-Level Authorization (IDOR): Any patient can supply another patient's ID. |
| **Appointments** | Doctor Queue & Slots | `AppointmentController.java:34`, `AvailabilityController.java:31` | `doctor/appointments/page.jsx`, `doctor/availability/page.jsx` | **IMPLEMENTED** | None | Slot calculations based on doctor availability work in memory. |
| **Appointments** | Cancel / Complete | `AppointmentController.java:44`, `AppointmentServiceImpl.java:134` | `doctor/appointments/page.jsx`, `patient/my-appointments/page.jsx` | **PARTIAL** | None | Status string `PENDING` not in `AppointmentStatus` enum; any patient can cancel any appointment. |
| **Doctor Directory**| Public Directory | `DoctorController.java:23`, `DoctorServiceImpl.java:40` | `app/page.jsx`, `patient/browse-doctors/page.jsx` | **PARTIAL** | None | Readme says Public, but `SecurityConfig.java:40` requires authentication (`hasAnyRole`). `DoctorServiceImpl.getAllDoctors` mutates DB on read. |
| **Doctor Directory**| Doctor Onboarding | `DoctorController.java:28`, `DoctorServiceImpl.java:24` | `admin/create-doctor/page.jsx` | **IMPLEMENTED** | None | Admin can create doctor profiles. Does not set custom `Doctor.id`. |
| **Prescriptions** | Issue Prescription | `PrescriptionController.java:24`, `PrescriptionServiceImpl.java:33` | `doctor/prescriptions/page.jsx` | **PARTIAL** | None | Compares `appointment.doctorId` with `doctor.userId`; if doctor created via AdminController, ID mismatch throws 404. |
| **Prescriptions** | Patient Prescriptions | `PrescriptionController.java:44`, `PrescriptionServiceImpl.java:100` | `patient/my-prescriptions/page.jsx` | **BROKEN** | None | Any authenticated patient can fetch another patient's prescriptions by altering URL parameter. |
| **Prescriptions** | PDF Download | `PrescriptionController.java:60`, `PdfServiceImpl.java:27` | `patient/my-prescriptions/page.jsx` | **PARTIAL** | `PrescriptionControllerTest.java` | Generates basic PDF via PDFBox, but accessible by any patient IDOR. |
| **Beds & Wards** | Bed Occupancy Matrix | `BedController.java:26`, `BedServiceImpl.java:28` | `admin/beds/page.jsx` | **IMPLEMENTED** | None | List and status indicators function correctly. |
| **Beds & Wards** | Bed Allocation | `BedController.java:37`, `BedServiceImpl.java:54` | `admin/beds/page.jsx` | **PARTIAL** | None | Zero concurrency protection. Two concurrent requests can allocate the same bed. |
| **Beds & Wards** | Patient Discharge | `BedController.java:48`, `BedServiceImpl.java:77` | `admin/beds/page.jsx` | **IMPLEMENTED** | None | Updates status to DISCHARGED and frees bed. |
| **Billing & Invoices**| Invoicing & Breakdown | `InvoiceController.java:19`, `InvoiceServiceImpl.java:20` | `admin/billing/page.jsx` | **IMPLEMENTED** | None | Generates automated invoice number and calculates 10% tax. |
| **Billing & Invoices**| Patient Invoices | `InvoiceController.java:31`, `InvoiceServiceImpl.java:52` | None | **PARTIAL** | None | Unprotected endpoint; no frontend patient billing history view exists. |
| **Pharmacy & Stock**| Medical Supplies | `InventoryController.java:19`, `InventoryServiceImpl.java:20` | None | **PARTIAL** | None | Backend exists, but frontend has no dedicated inventory view. |
| **Patient Health** | Medical History Timeline | `PatientController.java:54`, `PatientServiceImpl.java:87` | `patient/history/page.jsx` | **BROKEN** | None | `SecurityConfig` locks `/api/patients/**` to `ADMIN`. Patient gets 403 Forbidden. |
| **Patient Health** | Family Medical Profiles | `PatientController.java:59`, `PatientServiceImpl.java:142` | `patient/family/page.jsx` | **BROKEN** | None | `SecurityConfig` locks `/api/patients/**` to `ADMIN`. Patient gets 403 Forbidden. |
| **Patient Health** | Medicine Reminders | None (`MedicineReminder` entity only) | `patient/reminders/page.jsx`, `reminderService.js` | **BROKEN** | None | Frontend calls `/api/reminders/**` which does not exist on backend (404 Not Found). |
| **Real-Time Alerts**| WebSocket Notifications | `WebSocketConfig.java`, `NotificationServiceImpl.java` | `useWebSocket.js`, `NotificationListener.jsx` | **BROKEN** | None | Protocol conflict (`socket.io-client` vs Spring STOMP); missing STOMP session authentication. |
| **Background Jobs** | Midnight Token Cleanup | `TokenCleanupScheduler.java:26` | N/A | **MISSING** | None | Empty method body with log statement. No database cleanup performed. |
| **Background Jobs** | Appointment Reminders | `AppointmentReminderScheduler.java:25` | N/A | **MISSING** | None | Empty method body with debug log. No reminders queried or sent. |
| **Domain Events** | Decoupled Event Bus | `event/AppointmentEventPublisher.java` | N/A | **MISSING** | None | Publisher is never called; zero listeners exist in backend. Synchronous execution used. |
| **Analytics & KPI** | Executive Dashboard | `DashboardController.java:29`, `DashboardServiceImpl.java:27` | `dashboard/page.jsx`, `admin/dashboard/page.jsx` | **IMPLEMENTED** | None | Recharts metrics render properly with dynamic counts. |
| **System Health** | Telemetry Health Probe | `HealthController.java:19` | N/A | **PARTIAL** | `HealthControllerTest.java` | Static string response; does not test MongoDB connection or actual system health. |
