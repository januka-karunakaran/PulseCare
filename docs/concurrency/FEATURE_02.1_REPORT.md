# Feature 02.1 — Appointment & Bed Booking Concurrency Report

## 1. Executive Summary
Feature 02.1 resolves the critical concurrency vulnerability **ARCH-01** identified during Feature 00.1 baseline audit: the hospital platform previously relied on JVM-local `synchronized` blocks and non-atomic read-then-write patterns (`findMax + 1` for appointment tokens, `getBedById` -> `save` for beds). Under multi-instance or high-concurrency conditions, these patterns permitted:
1. Double-booking identical single-capacity doctor appointment slots.
2. Generating duplicate daily queue numbers for different patients.
3. Simultaneous allocation of the same hospital bed to multiple patients.
4. Race conditions during patient discharge leading to inconsistent bed occupancy.

This release introduces **database-enforced atomic concurrency controls** in MongoDB, eliminating JVM-local bottlenecks while ensuring full multi-instance safety, idempotency, strict state transitions, and user-friendly HTTP 409 conflict responses.

---

## 2. Baseline & Environment Metadata
- **Project:** PulseCare 2.0
- **Phase:** 02 — Core Hospital Operations Reliability
- **Feature:** 02.1 — Appointment & Bed Booking Concurrency
- **Base Branch:** `main`
- **Starting main SHA:** `dbdd37b88687097e564b0a2bbb678d9ec7f4d668`
- **Feature Branch:** `feature/02.1-booking-concurrency`

---

## 3. Architecture & Core Invariants

### 3.1 Doctor Appointment Slot Reservation
- **Slot Identity:** `canonicalDoctorId_appointmentDate_normalizedTime`
- **Enforcement Mechanism:** Dedicated MongoDB collection `appointment_slot_reservations` where the document `_id` is the slot key. MongoDB primary key uniqueness physically prevents two concurrent documents from occupying the same slot.
- **Doctor Identity Resolution:** Preserves Feature 01.1.9 security mappings: lookups accept either `Doctor.id` or `Doctor.userId`, but canonicalize to `Doctor.id` before reserving slots and incrementing daily queue counters.
- **Failure Recovery:** If appointment persistence fails after slot reservation, a compensating rollback immediately releases the reservation to prevent orphaned slots.
- **Cancellation & Rebooking:** Transitioning an appointment to `CANCELLED` safely releases its specific slot reservation, permitting immediate rebooking without corrupting historical records.

### 3.2 Monotonic Daily Queue Tokens
- **Counter Key:** `canonicalDoctorId_appointmentDate` in `daily_queue_counters`.
- **Atomic Generation:** Executed via MongoDB `findAndModify` with `$inc: { seq: 1 }` and `upsert: true`.
- **Guarantees:** Unique, monotonically increasing token numbers per doctor per day, immune to JVM restarts and instance count.

### 3.3 Atomic Hospital Bed Allocation & Safe Discharge
- **Claim Mechanism:** Bed allocation uses atomic conditional update:
  `Query.query(Criteria.where("_id").is(bedId).and("status").is("AVAILABLE"))`
  `Update.update("status", "OCCUPIED")`
- **Ownership Binding:** Bed is linked directly to `currentBookingId`.
- **Safe Discharge:** Discharge requires `BedBooking` to be in `ACTIVE` state:
  `Query.query(Criteria.where("_id").is(bookingId).and("status").is("ACTIVE"))`
  `Update.update("status", "DISCHARGED").set("dischargeDate", today)`
  The bed is then released only if it was occupied by that booking. Repeated discharge calls fail closed with HTTP 409 `INVALID_BOOKING_TRANSITION`.

---

## 4. Modified & Added Files

### Backend:
1. `backend/src/main/java/com/hospital/backend/model/AppointmentSlotReservation.java` (New)
2. `backend/src/main/java/com/hospital/backend/repository/AppointmentSlotReservationRepository.java` (New)
3. `backend/src/main/java/com/hospital/backend/model/DailyQueueCounter.java` (New)
4. `backend/src/main/java/com/hospital/backend/exception/AppointmentSlotConflictException.java` (New)
5. `backend/src/main/java/com/hospital/backend/exception/BedAlreadyOccupiedException.java` (New)
6. `backend/src/main/java/com/hospital/backend/exception/InvalidBookingTransitionException.java` (New)
7. `backend/src/main/java/com/hospital/backend/model/Bed.java` (Added `currentBookingId`)
8. `backend/src/main/java/com/hospital/backend/dto/common/ErrorResponse.java` (Added `code` field)
9. `backend/src/main/java/com/hospital/backend/exception/GlobalExceptionHandler.java` (Added 409 conflict handlers)
10. `backend/src/main/java/com/hospital/backend/service/impl/AppointmentServiceImpl.java` (Atomic slot reservation & queue counters)
11. `backend/src/main/java/com/hospital/backend/service/impl/AvailabilityServiceImpl.java` (Multi-identifier & reservation-aware availability)
12. `backend/src/main/java/com/hospital/backend/service/impl/BedServiceImpl.java` (Atomic conditional allocation & discharge)
13. `backend/src/test/java/com/hospital/backend/service/AppointmentServiceTest.java` (Added 5 concurrency & transition tests)
14. `backend/src/test/java/com/hospital/backend/service/BedServiceTest.java` (New unit test suite: 6 tests)
15. `backend/src/test/java/com/hospital/backend/service/BookingConcurrencyMongoIntegrationTest.java` (New Testcontainers integration suite)

### Frontend:
1. `frontend/services/api.js` (Enriched thrown Error with HTTP status and server error codes)
2. `frontend/app/patient/appointments/page.jsx` (Friendly 409 conflict handling, slot refresh, preserves form fields)
3. `frontend/app/admin/beds/page.jsx` (Friendly 409 conflict handling, submit disabling, live bed refresh)

### Documentation:
1. `docs/concurrency/FEATURE_02.1_REPORT.md`
2. `docs/concurrency/APPOINTMENT_CONCURRENCY.md`
3. `docs/concurrency/BED_ALLOCATION_CONCURRENCY.md`
4. `docs/concurrency/STATE_TRANSITION_MATRIX.md`
5. `docs/concurrency/INDEX_MIGRATION_PLAN.md`
6. `docs/concurrency/CONCURRENCY_TEST_REPORT.md`

---

## 5. Verification Summary
- **Backend Unit Tests:** 58/58 passed (0 failures, 0 skipped, 0 errors).
- **Frontend Lint:** 0 errors (11 warnings regarding Next.js image tags and dependencies).
- **Docker / Testcontainers:** Fail-closed guard verified in integration tests.
