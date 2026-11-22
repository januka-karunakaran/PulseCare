# Automated Appointment Reminder Scheduler

## 1. Timezone Handling & Business Rules

The `AppointmentReminderScheduler` evaluates appointments occurring within the 24-hour reminder window strictly in the hospital business timezone (`Asia/Colombo`).

```java
ZoneId zoneId = ZoneId.of("Asia/Colombo");
ZonedDateTime.of(localDate, localTime, zoneId).toInstant();
```

### Deterministic Clock Support
The scheduler supports an injected `java.time.Clock` (`setClock(Clock)`), enabling unit and regression test suites to test exact wall-clock moments deterministically.

---

## 2. Schedule-Aware Deduplication & Rescheduling Policy

If an appointment is rescheduled after a reminder was queued:
1. When rescheduling in `AppointmentServiceImpl`:
   `cancelPendingForAggregate(appointmentId)` immediately transitions prior pending outbox reminders to `CANCELLED`.
2. Deduplication Key Format:
   ```
   reminder:24h:<appointmentId>:<appointmentDate>@<appointmentTime>:<userId>:<channel>
   ```
   Because the key includes the date and time, a rescheduled appointment generates a new distinct deduplication key, allowing the new schedule to receive its appropriate 24-hour reminder.
3. Pre-Dispatch Verification in `OutboxProcessor`:
   When `OutboxProcessor` processes `APPOINTMENT_REMINDER_DUE`:
   - Checks `appointmentRepository.findById(aggregateId)`.
   - If appointment was deleted or status is no longer `CONFIRMED`/`PENDING`, the reminder is cancelled.
   - If the appointment date was changed from what is stored in outbox metadata, the reminder is cancelled with `APPOINTMENT_RESCHEDULED`.

---

## 3. Strict Time Parsing
- Disallowed silent default to 09:00 AM.
- If an appointment's time string is null, empty, or unparseable, `parseAppointmentStartInstant` returns `null` and logs a warning.
- Ensures malformed appointment data never triggers incorrect reminder notifications.
