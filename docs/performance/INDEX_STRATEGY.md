# MongoDB Indexing Strategy for PulseCare Search & Querying

## 1. Overview
In accordance with MongoDB query performance best practices (ESR rule: Equality, Sort, Range), compound and multikey indexes are defined across the core domain models to ensure high query performance, low memory usage, and zero collection-scanning bottlenecks.

---

## 2. Defined Indexes by Collection

### A. Collection: `appointments`
Domain Class: `com.hospital.backend.model.Appointment`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `appt_doc_date_idx` | `{"doctorId": 1, "appointmentDate": 1}` | Doctor schedule filtering, upcoming appointments, aggregation | Equality (`doctorId`), Range/Sort (`appointmentDate`) |
| `appt_patient_date_idx` | `{"patientId": 1, "appointmentDate": 1}` | Patient appointment history, upcoming patient visits | Equality (`patientId`), Range/Sort (`appointmentDate`) |
| `appt_status_date_idx` | `{"status": 1, "appointmentDate": 1}` | Admin operational filters, daily status queues | Equality (`status`), Range/Sort (`appointmentDate`) |

### B. Collection: `prescriptions`
Domain Class: `com.hospital.backend.model.Prescription`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `presc_doc_created_idx` | `{"doctorId": 1, "createdAt": 1}` | Doctor prescription logs sorted chronologically | Equality (`doctorId`), Sort (`createdAt`) |
| `presc_patient_created_idx` | `{"patientId": 1, "createdAt": 1}` | Patient medication history sorted chronologically | Equality (`patientId`), Sort (`createdAt`) |
| `presc_medicines_idx` | `{"medicines": 1}` | Multikey index for medicine keyword array searches | Multikey element match |

### C. Collection: `doctors`
Domain Class: `com.hospital.backend.model.Doctor`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `doctor_spec_rating_idx` | `{"specialization": 1, "rating": -1}` | Filter by medical specialty sorted by highest rating | Equality (`specialization`), Sort (`rating`) |
| `doctor_rating_idx` | `{"rating": -1}` | Top-rated doctor browsing | Sort (`rating`) |

### D. Collection: `users`
Domain Class: `com.hospital.backend.model.User`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `user_role_name_idx` | `{"role": 1, "fullName": 1}` | Role-scoped user directory search (patient/doctor lookup) | Equality (`role`), Prefix regex/Sort (`fullName`) |
| `user_role_spec_idx` | `{"role": 1, "specialization": 1}` | Role-scoped doctor specialization lookups | Equality (`role`), Equality (`specialization`) |

---

## 3. Query Plan & Regex Considerations
- Substring regex searches (e.g. `.*name.*`) cannot leverage B-tree index prefixes; however, equality prefixes on `role: "DOCTOR"` in `user_role_name_idx` constrain the initial candidate scan to only doctor users before regex evaluation.
- Medicine searches on `prescriptions.medicines` utilize the multikey array index `presc_medicines_idx`.
- Appointment count aggregations on `appointments` utilize `appt_doc_date_idx` for index-covered matching on `doctorId`.
