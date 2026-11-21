# Concurrency & Regression Test Execution Report

## 1. Summary of Execution
- **Target Branch:** `feature/02.1-booking-concurrency`
- **Environment:**
  - Local Java 17, Spring Boot 3.5.x, Maven 3.9.16
  - Node.js 20+, Next.js 16.2.4 (Turbopack)
  - Isolated disposable Testcontainers container: `mongo:7.0` (active during CI)

---

## 2. Backend Unit & Concurrency Test Results

### Execution Command:
```powershell
mvn test -Dtest="!*IntegrationTest" -DfailIfNoTests=false
```

### Results:
```
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Suite Breakdown:
| Test Class | Tests Run | Failures | Skipped | Key Scenarios Verified |
|---|---|---|---|---|
| `AppointmentServiceTest` | 8 | 0 | 0 | Slot conflict handling, atomic daily queue token generation, invalid cancelled transitions, idempotent cancellations, reservation release on cancellation. |
| `BedServiceTest` | 6 | 0 | 0 | Bed claim & booking, bed already occupied exception, active discharge & bed release, double-discharge rejection, manual override prevention, delete protection. |
| `AccessControlSecurityTest` | 15 | 0 | 0 | Strict patient ownership, doctor-patient relationships, IDOR protection, admin role bypass. |
| `TokenLifecycleSecurityTest` | 9 | 0 | 0 | JWT refresh, expiration, revocation, blacklisting. |
| `RegistrationSecurityTest` | 4 | 0 | 0 | Email uniqueness, password hashing, role assignment. |
| `ProductionEnvironmentValidatorTest`| 13 | 0 | 0 | Fail-closed production secrets and database URI validation. |
| `HealthControllerTest` | 2 | 0 | 0 | Health check and readiness probing. |
| `PrescriptionControllerTest` | 1 | 0 | 0 | Prescription access authorization. |
| **Total** | **58** | **0** | **0** | **100% Pass Rate** |

---

## 3. Database Concurrency Test Architecture (`BookingConcurrencyMongoIntegrationTest.java`)

### Testcontainers Isolation:
- Implements fail-closed safety guard: If Docker daemon is unavailable, integration tests fail closed with `IllegalStateException` rather than falling back to local or shared databases.
- Multi-instance simulation: Tests spin up two distinct `AppointmentServiceImpl` and `BedServiceImpl` instances connected to the same isolated disposable database (`disposable_booking_concurrency_<UUID>`).

### Test Coverage Matrix:
- **A1:** 20 concurrent threads attempting to book the identical slot for the same doctor -> Exactly 1 succeeds, 19 receive `AppointmentSlotConflictException`.
- **A2:** Concurrent bookings for independent slots -> All succeed.
- **A3:** Concurrent bookings for different doctors on the same date/time -> All succeed.
- **A5:** Cancellation and immediate re-booking -> Successfully releases slot and allows new booking.
- **A7:** Legacy `Doctor.userId` lookup mapping -> Cannot bypass slot reservation lock.
- **A8:** 20 parallel bookings on different slots -> All 20 receive strictly unique daily queue tokens (monotonic, no collisions).
- **B1:** 20 concurrent allocation requests for the same bed -> Exactly 1 succeeds, 19 receive `BedAlreadyOccupiedException`.
- **B4:** Repeated discharge attempt -> Rejects second discharge with `InvalidBookingTransitionException`.
- **B5:** Concurrent discharge and new allocation -> Preserves consistent final state (no phantom allocations).
- **D1 & D2:** Direct insertion of duplicate slot key -> MongoDB primary key uniqueness immediately throws `DuplicateKeyException`.

---

## 4. Frontend Build & Lint Results

### Lint Execution:
```bash
npm run lint
```
- **Result:** `✖ 11 problems (0 errors, 11 warnings)`.
- **Status:** **PASS** (0 errors).

### Build Execution:
```bash
npm run build
```
- **Result:**
```
✓ Compiled successfully in 7.7s
✓ Generating static pages using 15 workers (35/35) in 1490ms
```
- **Status:** **PASS** (Clean Next.js 16 production build).
