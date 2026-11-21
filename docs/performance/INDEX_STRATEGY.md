# MongoDB Indexing Strategy for PulseCare Search & Querying

## 1. Overview
In accordance with MongoDB query performance best practices (ESR rule: Equality, Sort, Range), compound and multikey indexes are defined across the core domain models to ensure high query performance, low memory usage, and zero collection-scanning bottlenecks.

---

## 2. Defined Indexes by Collection

### A. Collection: `appointments`
Domain Class: `com.hospital.backend.model.Appointment`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `appt_doc_date_idx` | `{"doctorId": 1, "appointmentDate": 1}` | Doctor schedule filtering, upcoming appointments by doctor | Equality (`doctorId`), Range/Sort (`appointmentDate`) |
| `appt_patient_date_idx` | `{"patientId": 1, "appointmentDate": 1}` | Patient appointment history, upcoming patient visits | Equality (`patientId`), Range/Sort (`appointmentDate`) |
| `appt_status_date_idx` | `{"status": 1, "appointmentDate": 1}` | Admin operational filters, daily status queues | Equality (`status`), Range/Sort (`appointmentDate`) |

### B. Collection: `prescriptions`
Domain Class: `com.hospital.backend.model.Prescription`

| Index Name | Key Definition | Purpose / Target Queries | ESR Alignment |
| :--- | :--- | :--- | :--- |
| `presc_doc_created_idx` | `{"doctorId": 1, "createdAt": -1}` | Doctor prescription logs sorted newest first | Equality (`doctorId`), Sort (`createdAt`) |
| `presc_patient_created_idx` | `{"patientId": 1, "createdAt": -1}` | Patient medication history sorted newest first | Equality (`patientId`), Sort (`createdAt`) |
| `presc_medicines_idx` | `{"medicines": 1}` | Multikey index for medicine keyword searches | Multikey element match |

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

## 3. Index Lifecycle & Application Startup
Spring Boot Data MongoDB manages index creation automatically via `@CompoundIndexes` on domain classes when auto-index creation is enabled in non-production environments (`spring.data.mongodb.auto-index-creation=true`). In production environments, database administrators can verify or apply these indexes using the MongoDB shell:

```javascript
db.appointments.createIndex({ doctorId: 1, appointmentDate: 1 }, { name: "appt_doc_date_idx" });
db.appointments.createIndex({ patientId: 1, appointmentDate: 1 }, { name: "appt_patient_date_idx" });
db.appointments.createIndex({ status: 1, appointmentDate: 1 }, { name: "appt_status_date_idx" });

db.prescriptions.createIndex({ doctorId: 1, createdAt: -1 }, { name: "presc_doc_created_idx" });
db.prescriptions.createIndex({ patientId: 1, createdAt: -1 }, { name: "presc_patient_created_idx" });
db.prescriptions.createIndex({ medicines: 1 }, { name: "presc_medicines_idx" });

db.doctors.createIndex({ specialization: 1, rating: -1 }, { name: "doctor_spec_rating_idx" });
db.doctors.createIndex({ rating: -1 }, { name: "doctor_rating_idx" });

db.users.createIndex({ role: 1, fullName: 1 }, { name: "user_role_name_idx" });
db.users.createIndex({ role: 1, specialization: 1 }, { name: "user_role_spec_idx" });
```
