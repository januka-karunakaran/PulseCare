# Feature 02.2 - Search & MongoDB Performance Optimization (Post-Correction Report)

## Executive Summary
This report documents the architectural corrections and optimizations implemented in Feature 02.2 for PulseCare 2.0. Following initial review, remaining unbounded rating materialization, incorrect doctor profile ID mappings (`Doctor.id != Doctor.userId`), in-memory appointment counting loops, and test stubbing issues were identified and resolved. All searches now execute purely via database-side query and aggregation pipelines in MongoDB, accompanied by batch hydration, healthcare zero-trust role boundaries, and strict HIPAA-aligned privacy protections.

---

## 1. Audit & Bottlenecks Resolved

| Subsystem / Operation | Pre-Correction Implementation | Defect / Vulnerability | Refactored Resolution |
| :--- | :--- | :--- | :--- |
| **Doctor Search Rating Filter** | Loaded all `Doctor` documents with `rating >= minRating` into Java memory | Unbounded memory allocation ($O(N)$ candidate materialization) | MongoDB Aggregation pipeline joining `users` and `doctors` via `$lookup`, applying `$match: { "doctorProfile.rating": { $gte: minRating } }` at DB level |
| **Doctor Profile ID Resolution** | Assumed `User.id == Doctor.id` using `doctorRepository.findAllById(userIds)` | Missed doctor profiles where `Doctor.id != Doctor.userId` | Lookup pipeline matches both `Doctor.userId` and `Doctor._id`. Resolves canonical `Doctor.id` in response |
| **Appointment Counting Storm** | Queried matching appointment documents and grouped via Java Streams | Unbounded collection transfer into JVM heap | MongoDB `$match` and `$group` aggregation on `appointments` collection; sums monthly and total counts across both profile and user identifiers without duplicate counting |
| **Patient Directory Search** | Included cancelled appointments when determining clinical relationships | Historical cancelled visits granted permanent clinical directory access | Filtered clinical relationship to `status != "CANCELLED"`; sanitised security logging to exclude patient emails |
| **Public Doctor Profile Privacy** | Disclosed personal phone number and license number publicly | Unnecessary staff privacy exposure | Sanitized public DTO to protect doctor personal phone and internal license numbers |
| **CI Backend Test Failure** | Unnecessary Mockito stubbing in validation tests | Build failure under strict Mockito rules | Removed redundant stubbings; all 70 unit tests pass cleanly |

---

## 2. Key Architecture & Design Implementations

### A. MongoDB-Side Join & Faceted Doctor Search
- The search pipeline starts on `users` collection filtered by `role: "DOCTOR"`, search term regex, and specialization.
- `$lookup` with a sub-pipeline joins `doctors` collection where either `userId` or `_id` matches the user's ID, with a `{ $limit: 1 }` guard.
- `$unwind` with `preserveNullAndEmptyArrays: true` handles doctors without separate profile documents.
- `$match` applies `minRating` filter at the database level.
- `$sort` orders records at the database level (by rating, experience, specialization, or name).
- `$facet` executes `totalCount` calculation and windowed `$skip` / `$limit` in a single database round-trip.

### B. Scalable Appointment Counting via Group Aggregation
- For the paginated doctor slice (at most `limit`, default 20), identifier aliases (`userId`, `profileId`) are collected.
- A single aggregation on `appointments` executes:
  - `$match`: `doctorId IN (aliases)`
  - `$group`: computes `totalCount: { $sum: 1 }` and `monthlyCount: { $sum: { $cond: [ currentMonth, 1, 0 ] } }`
- In-memory mapping sums counts across a doctor's canonical profile ID and legacy user ID without double counting.

### C. Clinical Data Authorization & Privacy
- **Patients:** Forbidden from searching the patient directory (`AccessDeniedException`).
- **Doctors:** Permitted only to search patients with active or completed clinical relationships (`status != "CANCELLED"`).
- **Public:** Personal phone numbers and internal license numbers omitted from public responses.
- **Logging:** Patient emails and search terms are excluded from security logs.

---

## 3. Test Verification Evidence
- **Backend Unit Tests:** 70 passing unit tests (0 failures, 0 errors, 0 skipped).
- **Search Service Tests:** 12 dedicated tests in `SearchServiceTest.java` validating:
  - Doctor search with `Doctor.id != Doctor.userId`
  - Canonical profile ID return
  - Rating filter and sorting
  - Appointment counting across alias IDs without duplicate counting
  - Patient role access restrictions
  - Appointment and prescription ownership enforcement
- **Frontend Validation:** `eslint` completed with 0 errors; production build successfully compiles all 35 routes.
