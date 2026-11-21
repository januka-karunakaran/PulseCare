# PulseCare Performance & Search Optimization Test Report

## 1. Test Suite Summary
Testing was conducted using JUnit 5, Mockito, and Spring Boot Test to validate both functional correctness and database performance characteristics.

- Total Unit Tests Executed: 69
- Total Failures: 0
- Total Errors: 0
- Total Skipped: 0
- Build Status: SUCCESS (execution time: 58.938 s)

---

## 2. Test Breakdown for Feature 02.2

### Unit Tests: `com.hospital.backend.service.SearchServiceTest`
1. `searchDoctors_WithFilters_BuildsDatabaseQueryAndBatchFetches`: Verifies MongoTemplate query construction with regex and ratings filters, ensuring batch loading of doctor stats with zero N+1 queries.
2. `searchDoctors_ExceedMaxLimit_ThrowsBadRequest`: Confirms that requested limits $> 100$ are rejected with HTTP 400 `BadRequestException`.
3. `searchDoctors_NegativeOffset_ThrowsBadRequest`: Validates that negative pagination offsets are rejected with HTTP 400.
4. `searchDoctors_InvalidSortField_ThrowsBadRequest`: Guards against sorting injection by rejecting unwhitelisted sort fields.
5. `searchPatients_AsPatient_ThrowsAccessDenied`: Validates privacy guard blocking patient users from accessing directory search with HTTP 403 `AccessDeniedException`.
6. `searchPatients_AsDoctor_RestrictedToAssociatedPatients`: Verifies that doctor queries only search patients with prior clinical relationships.
7. `searchPatients_AsAdmin_SearchesAllPatientsWithPagination`: Verifies admin search allows directory querying with pagination bounds.
8. `filterAppointments_AsPatient_ForcesOwnPatientId`: Validates that patient attempts to inspect another patient's records are overridden with their authenticated ID.
9. `filterAppointments_InvalidDateRange_ThrowsBadRequest`: Confirms `startDate > endDate` throws HTTP 400.
10. `filterAppointments_InvalidDateFormat_ThrowsBadRequest`: Confirms unparseable date strings throw HTTP 400.
11. `filterPrescriptions_MedicineSearch_BuildsArrayRegexQuery`: Validates MongoDB array query generation for medicines.

### Integration Tests: `com.hospital.backend.service.SearchPerformanceMongoIntegrationTest`
- Configured with Testcontainers disposable MongoDB container.
- Seeds synthetic datasets: 150 appointments, 100 prescriptions, 30 doctors, and 100 patients.
- Verifies database-side execution of regex queries, sorting, pagination offsets, and multikey array matching.

---

## 3. Complexity & Latency Comparison

| Metric / Scenario | Legacy Architecture | Refactored Feature 02.2 |
| :--- | :--- | :--- |
| **Doctor Search DB Calls** | $1 + 3N$ queries ($301$ queries for 100 doctors) | $3$ queries total (Users page, Doctor batch, Stats batch) |
| **Memory Consumption** | $O(N)$ full collection heap allocation | $O(\text{limit})$ bounded payload ($\le 100$ records) |
| **Filter & Sort Location** | JVM CPU via Java Stream | MongoDB Engine using B-Tree and Compound Indexes |
| **Patient Privacy Protection** | None (All patients returned to any caller) | Enforced RBAC + Clinical Relationship Isolation |
