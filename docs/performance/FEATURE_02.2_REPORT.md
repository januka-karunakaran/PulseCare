# Feature 02.2 - Search & MongoDB Performance Optimization Report

## Executive Summary
Feature 02.2 resolves critical performance and security scalability bottlenecks across the PulseCare 2.0 platform's search and filtering subsystems. Previously, searches executed unbounded collection dumps (`findAll()`, `findByRole()`) with in-memory Java Stream filtering, sorting, and pagination, accompanied by severe $O(N)$ query loops for metadata resolution. This feature eliminates all in-memory collection scans by introducing database-side indexing, MongoTemplate dynamic query composition, strict database-level pagination, batch relationship hydration, and zero-trust role-based data isolation.

---

## 1. Audit & Baseline Bottlenecks Identified

| Endpoint / Operation | Legacy Pattern | Problem / Bottleneck | Refactored Solution |
| :--- | :--- | :--- | :--- |
| **`GET /api/search/doctors`** | `userRepository.findAll()` -> Stream filter -> for each doctor: `doctorRepo.findById()` + 2x `appointmentRepo.findByDoctorId()` | Complete collection scan in JVM + $O(N)$ query storm (3 queries per doctor) | MongoTemplate database query with regex & rating filters, compound indexing, pageable limit/offset, batch metadata fetching |
| **`GET /api/search/patients`** | `userRepository.findByRole(PATIENT)` -> Stream filter | Unbounded scan + **Critical IDOR**: any authenticated patient could query and extract all other patients' personal data | Enforced clinical relationship checks: Patients denied (`AccessDeniedException`), Doctors restricted to existing clinical relationships, DB regex & pagination |
| **`POST /api/search/appointments`** | `appointmentRepository.findAll()` -> Stream filter -> for each appt: `userRepo.findById()` x 2 | Unbounded collection dump in JVM + $O(N)$ name lookups + no database date ranges | Database Criteria query on `appointmentDate`, status, reason, doctorId, patientId; batch User resolution via `findAllById` |
| **`POST /api/search/prescriptions`** | `prescriptionRepository.findAll()` -> Stream filter -> for each: `userRepo.findById()` x 2 | In-memory scans + stringification of medicine lists for substring matching | Native MongoDB array regex matching (`Criteria.where("medicines").regex`), database date ranges, batch User resolution |

---

## 2. Key Architecture & Design Implementations

### A. Database-Level Filtering, Sorting, and Pagination
- Replaced all in-memory streaming with `org.springframework.data.mongodb.core.MongoTemplate`.
- Implemented `Query` composition using `Criteria.where(...)`.
- Standardized pagination bounds across all endpoints:
  - Default `limit = 20`
  - Max `limit = 100`
  - Validation `offset >= 0`
- Query execution performs:
  1. `mongoTemplate.count(countQuery, Target.class)` for total matching records.
  2. `mongoTemplate.find(pageQuery.with(PageRequest.of(page, limit, sort)), Target.class)` retrieving only the requested slice.
- Return wrapper `SearchResultsWrapper<T>` populated with `totalCount`, `pageNumber`, `pageSize`, `totalPages`, and `hasMore`.

### B. Batch Hydration (Eliminating N+1 Queries)
- Doctor search previously queried `Doctor` document and ran two count queries per candidate. Refactored to fetch user documents first, extract IDs, and batch-query `doctorRepository.findAllById(doctorIds)` and batch appointment stats, dropping DB round-trips from $3N + 1$ to $3$ total queries.
- Appointment and Prescription filtering previously fetched patient and doctor names via individual queries inside a mapping loop. Refactored to extract all unique `patientId` and `doctorId` strings and issue a single `userRepository.findAllById(userIds)` call to populate an in-memory lookup map.

### C. Healthcare Data Authorization & Privacy
- **Doctor Search:** Public directory access with sanitization; excludes credentials and sensitive tokens.
- **Patient Directory Search:**
  - `PATIENT` role: Throws `AccessDeniedException("Patients are not authorized to search the patient directory")`.
  - `DOCTOR` role: Dynamically collects patient IDs associated with the calling doctor from appointments and prescriptions, restricting the database query to `Criteria.where("_id").in(associatedPatientIds)`.
  - `ADMIN` role: System-wide patient search with strict pagination.
- **Appointment & Prescription Ownership:**
  - `PATIENT` role: Overrides/enforces `patientId = currentUser.getId()` preventing IDOR.
  - `DOCTOR` role: Overrides/enforces `doctorId = currentUser.getId()`.
  - `ADMIN` role: Allows explicit filtering by doctor or patient.

### D. Input Sanitization & ReDoS Protection
- All user-supplied search strings undergo regex escaping via `Pattern.quote(term.trim())` before being compiled into MongoDB regular expressions (`Pattern.compile(escaped, Pattern.CASE_INSENSITIVE)`).
- Date inputs validated with `LocalDate.parse` (ISO `yyyy-MM-dd`) with logical constraint verification (`startDate <= endDate`).
- Sort field whitelist validation prevents MongoDB injection through unvalidated sort parameters.

---

## 3. Index Strategy

Compound indexes added across models to support prefix-matched, covered, and sorted queries:
1. **`appointments`**:
   - `{"doctorId": 1, "appointmentDate": 1}`
   - `{"patientId": 1, "appointmentDate": 1}`
   - `{"status": 1, "appointmentDate": 1}`
2. **`prescriptions`**:
   - `{"doctorId": 1, "createdAt": -1}`
   - `{"patientId": 1, "createdAt": -1}`
   - `{"medicines": 1}` (Multikey array index)
3. **`doctors`**:
   - `{"specialization": 1, "rating": -1}`
   - `{"rating": -1}`
4. **`users`**:
   - `{"role": 1, "fullName": 1}`
   - `{"role": 1, "specialization": 1}`

---

## 4. Verification & Test Evidence
- **Backend Unit Tests:** 69 passing unit tests (0 failures, 0 errors, 0 skipped), including 11 dedicated tests in `SearchServiceTest` verifying role authorization, pagination bounds, date validation, and array regex queries.
- **Frontend Validation:** Next.js lint passed with 0 errors. Production build compiled all 35 routes cleanly in 18.7 seconds.
- **Docker & CI/CD Readiness:** Safe Testcontainers isolation and Maven wrapper stability verified.
