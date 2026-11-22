# Automated Appointment Reminder Scheduler

## 1. Specification & Timezone Handling

The `AppointmentReminderScheduler` executes periodically (default every 15 minutes) to evaluate appointments occurring within the next 24-hour window.

### Hospital Business Timezone: `Asia/Colombo`
- Clinical appointment times are stored in local hospital wall-clock time (`yyyy-MM-dd` and `hh:mm a`).
- To prevent host JVM timezone skew, the scheduler explicitly binds to `ZoneId.of("Asia/Colombo")`.
- Local dates and times are resolved to UTC `Instant` values:
  ```java
  ZonedDateTime.of(localDate, localTime, ZoneId.of("Asia/Colombo")).toInstant();
  ```

---

## 2. Business Rules & Edge Cases

| Condition | Action Taken | Rationale |
|---|---|---|
| Appointment starts within $(T_{\text{start}} - 24\text{h}) \dots T_{\text{start}}$ | Enqueue `IN_APP` and `EMAIL` reminder | Standard 24h patient and doctor notice |
| Appointment starts $> 26$ hours away | Suppress | Not yet due for reminder |
| Appointment has already started / past | Suppress | Sending reminder after appointment start is confusing and invalid |
| Appointment status is `CANCELLED` | Suppress | Patients must not receive reminders for cancelled visits |
| Appointment status is `COMPLETED` | Suppress | No reminder needed for concluded visits |
| System restarts or delay occurs | Catch-up | Scheduler lookahead window captures due appointments safely |
| Duplicate reminder check | Deduplication key check | Prevents multiple notifications per appointment per recipient |

---

## 3. Deduplication Key Format

Reminders use deterministic keys:
- `reminder:24h:<appointmentId>:<patientUserId>:IN_APP`
- `reminder:24h:<appointmentId>:<patientUserId>:EMAIL`
- `reminder:24h:<appointmentId>:<doctorUserId>:IN_APP`

If an appointment is cancelled before delivery, `cancelPendingByAggregateId(appointmentId)` automatically transitions pending outbox entries to `CANCELLED`.
