# ðŸ§ª PulseCare 2.0 â€” Safe Test Execution & Evidence

**Document ID:** `AUDIT-00.1-TEST-EVIDENCE`
**Audited Commit:** `be6be662c1b50e58d5fe77439af2ce14e4f4f655`
**Execution Environment:** Windows 11, Oracle OpenJDK 17.0.12, Node.js 22.x, Docker Compose v2.x
**Lead QA Engineer / Security Reviewer**

---

## 1. Summary of Execution Results

| Layer / Target | Toolchain / Command | Status | Total Tests | Passed | Failed | Skipped | Notes & Diagnostic Details |
|---|---|---|---|---|---|---|---|
| **Backend Unit & Context Tests** | `cd backend; .\mvnw.cmd test` | **PASSED** | 7 | 7 | 0 | 0 | Spring Boot 3.5.6, JUnit Platform, MockMvc. Requires running MongoDB for context tests. |
| **Frontend Static Analysis** | `cd frontend; npm run lint` | **PASSED** | N/A | N/A | 0 | 0 | ESLint v9. 0 errors, 11 warnings (img elements, hook dependencies). |
| **Frontend Unit Tests** | `cd frontend; npm test` | **NOT RUN** | N/A | N/A | N/A | N/A | **Missing script:** `"test"` is not defined in `frontend/package.json`. |
| **Frontend Production Build** | `cd frontend; npm run build` | **PASSED** | 35 Routes | 35 | 0 | 0 | Next.js 16.2.4 (Turbopack) build succeeded in 19.3s. Generated 35 static/dynamic routes. |
| **Docker Compose Dev Config** | `docker compose config --quiet` | **PASSED** | N/A | N/A | N/A | N/A | Syntax valid; warning regarding obsolete `version` attribute. |
| **Docker Compose Prod Config**| `docker compose -f docker-compose.prod.yml config --quiet` | **PASSED** | N/A | N/A | N/A | N/A | Syntax valid; warning: `JWT_SECRET` defaults to empty string if unset. |

---

## 2. Backend Automated Test Suite Execution

### 2.1 Command Executed
```powershell
cd backend
.\mvnw.cmd test
```

### 2.2 Execution Log Evidence
```text
[INFO] Scanning for projects...
[INFO] ------------------------< com.hospital:backend >------------------------
[INFO] Building backend 0.0.1-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- compiler:3.14.0:compile (default-compile) @ backend ---
[INFO] Compiling 159 source files with javac [debug parameters release 17] to target\classes
[INFO]
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ backend ---
[INFO] Compiling 6 source files with javac [debug parameters release 17] to target\test-classes
[WARNING] .../PrescriptionControllerTest.java:[27,6] org.springframework.boot.test.mock.mockito.MockBean in org.springframework.boot.test.mock.mockito has been deprecated and marked for removal
[INFO]
[INFO] --- surefire:3.5.4:test (default-test) @ backend ---
[INFO] Running com.hospital.backend.BackendApplicationTests
2026-11-19 07:55:54 INFO  Monitor thread successfully connected to server with description ServerDescription{address=localhost:27017, type=STANDALONE, state=CONNECTED}
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.BackendApplicationTests
[INFO] Running com.hospital.backend.controller.HealthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.controller.HealthControllerTest
[INFO] Running com.hospital.backend.controller.PrescriptionControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.controller.PrescriptionControllerTest
[INFO] Running com.hospital.backend.security.JwtServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.security.JwtServiceTest
[INFO] Running com.hospital.backend.service.AppointmentServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.service.AppointmentServiceTest
[INFO] Running com.hospital.backend.service.AuthServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.backend.service.AuthServiceTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 2.3 Key Backend Findings
1. **Total Tests:** 7 tests executed across 6 test classes.
2. **Passed:** 7 (100% pass rate).
3. **Database Coupling:** Three test classes (`BackendApplicationTests`, `HealthControllerTest`, `PrescriptionControllerTest`) are annotated with `@SpringBootTest`. Because `application-test.properties` points to `mongodb://localhost:27017/hospital_management_test_db`, these tests require an active MongoDB server on port 27017. If MongoDB is stopped, these tests fail during context initialization.
4. **Deprecation Warnings:** `org.springframework.boot.test.mock.mockito.MockBean` is deprecated in Spring Boot 3.4+ and will be removed in Spring Boot 3.6+. Tests must be migrated to `@MockitoBean`.

---

## 3. Frontend Static Analysis & Lint Checks

### 3.1 Command Executed
```powershell
cd frontend
npm run lint
```

### 3.2 Execution Log Evidence
```text
> frontend@0.1.0 lint
> eslint

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\login\page.jsx
  459:13  warning  Using `<img>` could result in slower LCP and higher bandwidth. Consider using `<Image />` from `next/image`  @next/next/no-img-element

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\page.jsx
  884:15   warning  Using `<img>` could result in slower LCP  @next/next/no-img-element
  1011:15  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element
  1029:15  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element
  1047:15  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\patient\family\page.jsx
  28:6  warning  React Hook useEffect has a missing dependency: 'loadFamily'. Either include it or remove the dependency array  react-hooks/exhaustive-deps

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\patient\reminders\page.jsx
  28:6  warning  React Hook useEffect has a missing dependency: 'loadReminders'. Either include it or remove the dependency array  react-hooks/exhaustive-deps

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\profile\page.jsx
  134:23  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\register\page.jsx
  410:13  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\app\search\page.jsx
  79:21  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element

C:\Users\ASUS\Desktop\HospitalManagementSystem\frontend\components\DoctorSearch.jsx
  242:25  warning  Using `<img>` could result in slower LCP  @next/next/no-img-element

âœ– 11 problems (0 errors, 11 warnings)
```

### 3.3 Key Frontend Lint Findings
1. Zero syntax or compilation errors detected.
2. 11 warnings:
   - 9 instances of unoptimized raw HTML `<img>` elements instead of Next.js `<Image />`.
   - 2 instances of missing dependencies in `useEffect` dependency arrays (`family/page.jsx`, `reminders/page.jsx`).

---

## 4. Frontend Unit Test Limitations

### 4.1 Evaluation
The repository contains:
- `frontend/jest.config.js`
- `frontend/jest.setup.js`
- `frontend/__tests__/utils/formatters.test.js`
- `frontend/__tests__/utils/validators.test.js`

However, inspection of `frontend/package.json` reveals:
```json
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint"
  }
```
### 4.2 Status: **NOT RUN â€“ Missing Script & Dependencies**
1. Executing `npm test` fails immediately with `npm error Missing script: "test"`.
2. Furthermore, Jest (`jest`), `jest-environment-jsdom`, and `@testing-library/react` are not declared in `devDependencies` in `frontend/package.json`.
3. Consequently, unit test suites cannot be executed without adding the missing dependencies and package script.

---

## 5. Docker Infrastructure Validation

### 5.1 Development Compose Stack (`docker-compose.yml`)
```powershell
docker compose config --quiet
```
- **Exit Code:** `0`
- **Warnings:**
  `the attribute version is obsolete, it will be ignored, please remove it to avoid potential confusion`
- **Result:** Successfully validated container configuration.

### 5.2 Production Compose Stack (`docker-compose.prod.yml`)
```powershell
docker compose -f docker-compose.prod.yml config --quiet
```
- **Exit Code:** `0`
- **Warnings:**
  `The "JWT_SECRET" variable is not set. Defaulting to a blank string.`
  `the attribute version is obsolete, it will be ignored, please remove it to avoid potential confusion`
- **Result:** Successfully validated. Highlights that `JWT_SECRET` must be set explicitly in production to prevent blank signature strings.
