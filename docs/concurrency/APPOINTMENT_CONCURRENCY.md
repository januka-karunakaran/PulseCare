# Appointment Concurrency & Double-Booking Elimination

## 1. Problem Statement
In previous versions, appointment booking relied on JVM-level `public synchronized Appointment createAppointment(...)` in `AppointmentServiceImpl.java`. Daily queue numbers were computed by scanning all appointments for that doctor and day:
```java
int nextTokenNumber = appointmentsForDay.stream()
        .map(Appointment::getTokenNumber)
        .filter(token -> token != null)
        .max(Integer::compareTo)
        .orElse(0) + 1;
```
This design had two fatal architectural vulnerabilities:
1. **Multi-Instance Failure:** `synchronized` applies only to a single JVM heap. When multiple backend instances run behind a reverse proxy or load balancer, concurrent requests for the same doctor and slot on separate containers both succeed, double-booking the doctor.
2. **Duplicate Token Race:** Concurrent requests read the same existing max token, generating duplicate token numbers (e.g., two patients receiving Token #5 on the same day).

---

## 2. Slot Identity & Normalization
A single-capacity doctor appointment slot is identified by:
- `canonicalDoctorId`: The MongoDB `_id` of the `Doctor` profile (resolved from either `Doctor.id` or `Doctor.userId`).
- `appointmentDate`: Normalized `YYYY-MM-DD` (e.g. `2026-12-01`).
- `appointmentTime`: Normalized uppercase trimmed string (e.g. `10:30 AM`).

Composite Primary Key:
```
slotKey = String.format("%s_%s_%s", canonicalDoctorId, normalizedDate, normalizedTime);
```

---

## 3. Database-Enforced Slot Reservation Design
Rather than relying on application locks, slot claims are enforced directly at the MongoDB engine layer via a dedicated collection:
```
Collection: appointment_slot_reservations
_id: canonicalDoctorId_appointmentDate_normalizedTime
```

### 3.1 Booking Flow
1. **Doctor Resolution:** Incoming `doctorId` is checked against `DoctorRepository.findById()` or `DoctorRepository.findByUserId()`. Canonical `Doctor.id` is extracted.
2. **Availability Check:** Validates that the doctor has configured availability for this date and time.
3. **Atomic Slot Claim:** Attempts to insert `AppointmentSlotReservation` with `_id: slotKey`:
   - If no reservation exists, the insert succeeds in `O(1)`.
   - If another request claims the slot simultaneously, MongoDB engine rejects the second insert with `DuplicateKeyException`.
   - The application translates `DuplicateKeyException` to `AppointmentSlotConflictException` (HTTP 409 Conflict, `APPOINTMENT_SLOT_CONFLICT`).
4. **Atomic Daily Queue Token:** MongoDB `findAndModify` atomically increments the counter in `daily_queue_counters`.
5. **Appointment Persistence:** `Appointment` record is created in status `PENDING` with the guaranteed unique token. The slot reservation is updated with the persistent `appointmentId`.
6. **Compensating Rollback:** If appointment saving throws an unexpected exception, a compensating rollback automatically removes the slot reservation to prevent orphaned locks.

---

## 4. Atomic Daily Queue Token Generation
- **Collection:** `daily_queue_counters`
- **Identifier:** `canonicalDoctorId_appointmentDate`
- **Atomic Operation:**
```java
Query query = Query.query(Criteria.where("_id").is(counterKey));
Update update = new Update()
        .inc("seq", 1)
        .setOnInsert("doctorId", canonicalDoctorId)
        .setOnInsert("date", normalizedDate)
        .set("updatedAt", Instant.now());
DailyQueueCounter counter = mongoTemplate.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true).upsert(true), DailyQueueCounter.class);
```
- Multi-node safe: Handled inside MongoDB's atomic document locking.
- Restarts safe: Counter state persists in the database.
- Monotonic: Every booking receives an incrementing integer.

---

## 5. Cancellation & Rebooking Lifecycle
- When an appointment transitions to `CANCELLED` or `REJECTED`:
  The slot reservation is deleted matching `appointmentId = appointment.getId()`:
  `slotReservationRepository.deleteByAppointmentId(appointment.getId())`
- **Rebooking Safety:** Once deleted, legitimate patients can immediately re-book that slot.
- **Ownership Invariant:** A cancellation will never delete or invalidate a reservation belonging to another patient.
