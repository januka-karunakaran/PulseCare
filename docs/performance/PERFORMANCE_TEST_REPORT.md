# PulseCare Performance & Search Optimization Test Report

## 1. Test Suite Summary
Testing was conducted using JUnit 5, Mockito, and Spring Boot Test to validate both functional correctness and database performance characteristics.

- Total Unit Tests Executed: 70
- Total Failures: 0
- Total Errors: 0
- Total Skipped: 0
- Build Status: SUCCESS (Execution time: ~49 s)

---

## 2. Test Breakdown for Feature 02.2

### Unit Tests: `com.hospital.backend.service.SearchServiceTest`
1. `searchDoctors_ValidCriteria_ReturnsPaginatedResultsWithCorrectProfileMapping`: Validates MongoDB aggregation pipeline, separate `Doctor.id != Doctor.userId` profile resolution, canonical doctor ID return, and appointment count summing across aliases without duplicate counting.
2. `searchDoctors_InvalidLimit_ThrowsBadRequest`: Confirms that limits $\le 0$ or $> 100$ throw HTTP 400.
3. `searchDoctors_InvalidOffset_ThrowsBadRequest`: Validates negative offset rejection.
4. `searchDoctors_InvalidSortField_ThrowsBadRequest`: Guards against sorting injection by rejecting unwhitelisted sort fields.
5. `searchDoctors_InvalidMinRating_ThrowsBadRequest`: Confirms `minRating < 0.0` or `minRating > 5.0` throws HTTP 400.
6. `searchPatients_WhenRequestedByPatient_ThrowsAccessDenied`: Validates privacy guard blocking patient users from accessing directory search with HTTP 403 `AccessDeniedException`.
7. `searchPatients_WhenRequestedByDoctorWithoutRelationship_ReturnsEmpty`: Verifies that doctor queries only search patients with prior clinical relationships (excluding cancelled visits).
8. `searchPatients_WhenRequestedByAdmin_ReturnsMatchingPatients`: Verifies admin search allows directory querying with pagination bounds.
9. `filterAppointments_WhenRequestedByPatient_EnforcesPatientOwnership`: Validates that patient attempts to inspect another patient's records are overridden with their authenticated ID.
10. `filterAppointments_InvalidDateRange_ThrowsBadRequest`: Confirms `startDate > endDate` throws HTTP 400.
11. `filterAppointments_InvalidDateFormat_ThrowsBadRequest`: Confirms unparseable date strings throw HTTP 400.
12. `filterPrescriptions_MedicineSearch_BuildsArrayRegexQuery`: Validates MongoDB array query generation for medicines.

### Integration Tests: `com.hospital.backend.service.SearchPerformanceMongoIntegrationTest`
- Configured with Testcontainers disposable MongoDB container (`mongo:7.0`).
- Seeds synthetic datasets: 100 doctors with separate profile IDs, 200 appointments, and 50 prescriptions.
- Verifies database-side execution of regex queries, sorting, pagination offsets, and multikey array matching.

---

## 3. Complexity & Latency Comparison

| Metric / Scenario | Legacy Architecture | Refactored Feature 02.2 |
| :--- | :--- | :--- |
| **Doctor Search DB Calls** | $1 + 3N$ queries ($301$ queries for 100 doctors) | **2 queries total** (1 faceted aggregation + 1 appointment count group) |
| **Heap Memory Consumption** | $O(N)$ full collection heap allocation | **$O(\text{limit})$ bounded payload ($\le 100$ records)** |
| **Filter & Sort Location** | JVM CPU via Java Stream | **MongoDB Engine using compound indexes and aggregations** |
| **Rating Filtering** | All candidates loaded into JVM | **Filtered inside MongoDB aggregation `$match`** |
| **Patient Privacy Protection** | None (All patients returned to any caller) | **Strict RBAC + Clinical Relationship Isolation** |
| **Public Field Protection** | Personal phone & license disclosed | **Sanitized DTO protecting staff private data** |
