# Feature 01.2: Infrastructure, Container, CI/CD & Secrets Hardening — Correction Report

## Executive Summary
**Project:** PulseCare 2.0 Hospital Management System  
**Phase:** 01 — Platform Foundation  
**Feature:** 01.2 — Infrastructure, Container, CI/CD & Secrets Hardening (Pre-Merge Corrections)
**Target Base Branch:** `main`
**Feature Branch:** `feature/01.2-infrastructure-hardening`
**Pull Request Link:** [PR #11](https://github.com/januka-karunakaran/PulseCare/pull/11)

This report details the investigation, root-cause analysis, and pre-merge corrections implemented to resolve the blocking CI failures identified in Feature 01.2 PR #11.

---

## 1. Original GitHub Actions CI Failures & Root Causes

### 1.1 Original CI Failure Logs
In GitHub Actions run #44786209539 (`HMS Enterprise CI Pipeline`) and #44786209271 (`Hospital Management System CI/CD`), the backend test job failed immediately during the compile phase:
```text
[ERROR] COMPILATION ERROR :
[ERROR] /home/runner/work/PulseCare/PulseCare/backend/src/main/java/com/hospital/backend/controller/HealthController.java:[64,79]
incompatible types: java.util.Map<java.lang.String,java.lang.Object> cannot be converted to int
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile (default-compile) on project backend: Compilation failure
```

### 1.2 Root Causes Identified
1. **Method Signature Mismatch in `ApiResponse.java`:**
   - In `HealthController.java`, the readiness endpoint returned `ApiResponse.error("Service dependencies not ready", status)`.
   - `ApiResponse.java` only provided `error(String message, int statusCode)`. The Map payload was passed into the `int statusCode` parameter, triggering a compilation failure.
2. **Spring Context Dependency Collisions in Controller Slice Tests:**
   - `HealthControllerTest` and `PrescriptionControllerTest` used `@SpringBootTest` while mocking `MongoTemplate` via `@MockBean`. In Spring Boot, replacing `MongoTemplate` causes `MongoOperations.getConverter()` to return `null`, breaking Spring Data MongoDB repository bootstrap (`UserRepository`).
3. **Testcontainers Container Lifecycle Order:**
   - Spring Boot's `@DynamicPropertySource` evaluated before Testcontainers started the container, which could prevent binding dynamic ports unless pre-started.
4. **Docker Build Context Exclusion:**
   - `backend/.dockerignore` previously excluded `.mvn/wrapper/maven-wrapper.jar`, which created build instability when relying on wrapper execution inside container builds.
5. **Compose Production Secret Validation:**
   - `docker-compose.prod.yml` used standard expansion `${VAR}` rather than mandatory fail-closed expansion `${VAR:?error}`.

---

## 2. Corrections Implemented

### 2.1 API Response & Health Probe Fix
- Added overloaded method `public static <T> ApiResponse<T> error(String message, int statusCode, T data)` in `ApiResponse.java`.
- Updated `HealthController.java` to invoke `ApiResponse.error("Service dependencies not ready", HttpStatus.SERVICE_UNAVAILABLE.value(), status)`.
- Verified `/api/health`, `/api/health/liveness`, and `/api/health/readiness` return valid HTTP 200 / 503 status codes with zero sensitive internal detail leakage.

### 2.2 Controller Testing Optimization
- Refactored `HealthControllerTest` and `PrescriptionControllerTest` to `@WebMvcTest(controllers = ...)`.
- Mocked required security filter dependencies (`JwtService`, `CustomUserDetailsService`) to isolate web slices completely from database scanning and network sockets.
- Tested: All 4 `HealthControllerTest` tests and `PrescriptionControllerTest` passed in under 5 seconds.

### 2.3 Early-Startup Production Validator & Shannon Entropy Validation
- Refactored `ProductionEnvironmentValidator` from `ApplicationRunner` to `InitializingBean`.
- `afterPropertiesSet()` validates configuration during bean lifecycle, strictly BEFORE embedded Tomcat binds to the network port.
- Added mathematical Shannon entropy checking:
  - Requires minimum length of 32 bytes (256 bits).
  - Enforces distinct character set >= 12 and Shannon entropy >= 3.0 bits/char.
  - Rejects repetitive or trivial patterns (e.g. `aaaaaaaa...`).
- Tested: All 7 `ProductionEnvironmentValidatorTest` tests passed.

### 2.4 Testcontainers Lifecycle & Fail-Closed Guards
- Added explicit static initialization block in `RefreshTokenMongoIntegrationTest` so that when Docker is present, `MongoDBContainer("mongo:7.0")` starts before `@DynamicPropertySource` evaluates.
- Verified fail-closed behavior: when run on a local host lacking Docker daemon, the test fails closed with `IllegalStateException` in 0.9s and never attempts connection to local MongoDB.

### 2.5 Container Build Hardening & Compose Validation
- Updated `backend/Dockerfile` to use `mvn` directly from `maven:3.9-eclipse-temurin-17`, decoupling from wrapper jar dependencies.
- Added mandatory variable assertions `${VAR:?error}` across all secrets in `docker-compose.prod.yml`.
- Added fail-closed compose tests in `.github/workflows/ci.yml`.

---

## 3. Test & Validation Evidence

| Component / Test Suite | Executed | Passed | Failed | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Backend Unit & Slice Tests** | 47 | 47 | 0 | **100% PASS** |
| `ProductionEnvironmentValidatorTest` | 7 | 7 | 0 | **PASS** |
| `HealthControllerTest` | 4 | 4 | 0 | **PASS** |
| `PrescriptionControllerTest` | 1 | 1 | 0 | **PASS** |
| `AccessControlSecurityTest` | 15 | 15 | 0 | **PASS** |
| `TokenLifecycleSecurityTest` | 9 | 9 | 0 | **PASS** |
| `RegistrationSecurityTest` | 4 | 4 | 0 | **PASS** |
| `AppointmentServiceTest` | 3 | 3 | 0 | **PASS** |
| `AuthServiceTest` | 2 | 2 | 0 | **PASS** |
| `JwtServiceTest` | 1 | 1 | 0 | **PASS** |
| `BackendApplicationTests` | 1 | 1 | 0 | **PASS** |
| **Frontend Lint (`npm run lint`)** | Clean | Clean | 0 | **PASS (0 errors)** |
| **Frontend Build (`npm run build`)** | 35 routes | 35 routes | 0 | **PASS** |
| **Testcontainers Integration Test** | Fail-Closed | Guard Verified | 0 | **VERIFIED (Docker required)** |
| **Compose Schema Validation** | Validated | Validated | 0 | **PASS** |
