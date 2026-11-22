# CI/CD Consolidation & Feature 02.4 Validation Report

## 1. Resolution Summary
- **PR:** #15 (`https://github.com/januka-karunakaran/PulseCare/pull/15`)
- **Branch:** `feature/02.4-clinical-records`
- **Target:** `main`
- **Status:** CI Pipeline Consolidated, Backend Security Isolation Corrected.

---

## 2. CI Failure Investigation & Root Cause

### Symptoms
Both `HMS Enterprise CI Pipeline` and `Hospital Management System CI/CD` failed on PR #15 with exit code 1.

### Root Cause
In `ClinicalAuthorizationTest.java`, an authenticated Mockito security context was created using:
```java
SecurityContextHolder.getContext().setAuthentication(auth);
```
without explicitly resetting `SecurityContextHolder` in `@AfterEach` tearDown.
When Maven Surefire executed subsequent test suites in the same JVM, `AccessControlSecurityTest` inherited the contaminated `SecurityContextHolder`. When `AccessControlSecurityTest.patientA_AccessingOwnInvoice_Succeeds` was executed, Mockito detected an argument mismatch between the expected user `"patientA@test.com"` and the leaked authentication `"patientA@email.com"`, resulting in `PotentialStubbingProblem`.

### Resolution Applied
1. Updated `ClinicalAuthorizationTest.java`:
   - Added explicit `@BeforeEach` and `@AfterEach` handlers calling `SecurityContextHolder.clearContext()`.
   - Used `SecurityContextHolder.createEmptyContext()` prior to setting authentication.
2. Updated `AccessControlSecurityTest.java`:
   - Enforced `SecurityContextHolder.createEmptyContext()` inside `authenticateAs()` to prevent cross-test leakage.

---

## 3. Workflow Consolidation

### Removed Duplicate Pipelines
- Removed `.github/workflows/ci-cd.yml` which caused duplicate pull-request checks.
- Migrated `publish-images` job into `.github/workflows/ci.yml` with strict gate:
  `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`.

### Maven Invocation Hardening
- Eliminated `./mvnw -B test || mvn -B test`.
- Standardized on `./mvnw -B test` with automatic Surefire report upload upon test failure.

---

## 4. Verification Results

| Gate | Execution | Result |
| :--- | :--- | :--- |
| Backend Compilation | `./mvnw -B test-compile` | SUCCESS |
| Unit & Security Tests | `./mvnw -B test -Dtest="!*MongoIntegrationTest"` | 121 Tests Run, 0 Failures, 0 Errors |
| Clinical Authorization | `ClinicalAuthorizationTest` | 7 Tests Run, 0 Failures |
| Access Control Security | `AccessControlSecurityTest` | 15 Tests Run, 0 Failures |
| Frontend Lint | `npm run lint` | 0 Errors, 11 non-blocking Next.js image warnings |
| Frontend Build | `npm run build` | 35 routes compiled successfully |
| Local Compose Config | `docker compose config --quiet` | SUCCESS |
| Production Compose | Fail-closed verification without secrets | SUCCESS (Rejected correctly) |
