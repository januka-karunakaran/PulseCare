# PulseCare 2.0 — Security Test Report

**Execution Date:** 2026-11-19
**Feature Branch:** `fix/01.1.11-test-database-isolation`
**Test Suite Target:** Spring Boot Backend Unit, Access Control, and Isolated MongoDB Integration Tests
**Execution Environment:** Windows 11, JDK 17.0.12, Maven Wrapper, Spring Boot 3.5.6, Isolated Test MongoDB
**Result:** **41 TESTS RUN, 0 FAILURES, 0 ERRORS, 0 SKIPPED (BUILD SUCCESS)**

---

## 1. Executive Summary

Feature 01.1.11 hardened the MongoDB security integration tests with an isolated test database architecture, dynamic Testcontainers support, fail-closed destructive safeguards, index environment policies, and corrected query assertions.

The backend test suite executes **41 tests across 9 test classes**, with a 100% pass rate.

| Test Class | Category | Tests Executed | Passed | Failed | Status |
|---|---|---|---|---|---|
| `RefreshTokenMongoIntegrationTest` | Isolated Multi-Instance MongoDB Concurrency Integration | 4 | 4 | 0 | **PASS** |
| `TokenLifecycleSecurityTest` | Unit Token Rotation, Invalidation, SHA-256 Hashing | 9 | 9 | 0 | **PASS** |
| `AccessControlSecurityTest` | BOLA / IDOR / Identity Mapping | 15 | 15 | 0 | **PASS** |
| `RegistrationSecurityTest` | Privilege Escalation (SEC-02) | 4 | 4 | 0 | **PASS** |
| `AppointmentServiceTest` | Patient/Doctor Identity Mapping & Schedule | 3 | 3 | 0 | **PASS** |
| `PrescriptionControllerTest` | Prescription Endpoint & PDF Generation | 1 | 1 | 0 | **PASS** |
| `JwtServiceTest` | JWT Signing & Parsing | 1 | 1 | 0 | **PASS** |
| `AuthServiceTest` | Authentication Logic | 2 | 2 | 0 | **PASS** |
| `BackendApplicationTests` | Spring Boot Context Loading | 2 | 2 | 0 | **PASS** |
| **Total** | | **41** | **41** | **0** | **100% PASS** |

---

## 2. Test Database Isolation Architecture & Destructive Safeguards

### A. Isolated Test Database Configuration
- **Property Profile:** `@ActiveProfiles("test")` loads `application-test.properties`.
- **Testcontainers Dynamic Discovery:** `RefreshTokenMongoIntegrationTest` dynamically initializes an isolated, disposable `MongoDBContainer("mongo:7.0")` with dynamic database naming (`disposable_test_<uuid>`). Dynamic properties `@DynamicPropertySource` bind `spring.data.mongodb.uri` to the container's replica set URL.
- **Fail-Closed Destructive Safeguard:**
  - Zero tolerance for shared database fallback: If Docker is unavailable, the test fails closed immediately with an `IllegalStateException` rather than silently falling back to `localhost:27017` or any shared database.
  - Before executing any destructive operation (`dropCollection` or `dropDatabase`), the test suite inspects `mongoTemplate.getDb().getName()`. If the connected database does not start with `"disposable_test_"` or contains `"prod"` or matches `"hospital_management_db"`, the test throws an immediate `IllegalStateException` and halts.

### B. Corrected Test Assertions
1. **Old Token Removal:**
   - Corrected token assertion to query the MongoDB `token` field (`Query.query(Criteria.where("token").is(hashedOldToken))`) rather than `findById` (which queries document `_id`). Verified the consumed token is completely removed from the database.
2. **New Token Persistence:**
   - Queried by `Criteria.where("token").is(hashedNewToken)` to verify the replacement token was safely stored at rest with SHA-256 hashing.
3. **Unique Index Verification:**
   - Added test `uniqueTokenIndex_PreventsDuplicateTokensInDatabase` verifying that attempting to persist duplicate tokens triggers a `DuplicateKeyException` from the MongoDB engine.
4. **Concurrency Race Verification:**
   - Simulated 10 concurrent threads across 2 independent `RefreshTokenServiceImpl` service instances sharing the database. Verified exactly 1 request succeeded while 9 requests failed with expected `BadRequestException("Invalid or revoked refresh token")`.

---

## 3. Index Deployment Policy

- **Development & Testing:** `spring.data.mongodb.auto-index-creation=true` ensures unique indexes (such as `RefreshToken.token`) are created automatically during development and integration test execution.
- **Production (`application-prod.properties`):** `spring.data.mongodb.auto-index-creation=false` disables automatic synchronous index creation at startup to avoid collection locking and deployment timeouts during rolling restarts. Production indexes are deployed out-of-band via database migration scripts.

---

## 4. CI/CD Integration & Pipeline Safety

- **GitHub Actions Workflows (`ci.yml`, `ci-cd.yml`):**
  - Backend integration tests run against isolated database `hospital_management_test_isolated` backed by containerized MongoDB services.
  - Removed `continue-on-error: true` on frontend lint checks so that lint failures block merging.
  - Zero database credentials or secrets are committed or echoed in CI logs.
