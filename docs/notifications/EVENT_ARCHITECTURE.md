# Event-Driven Architecture Specification

## 1. Domain Event Model

PulseCare adopts an internal application event architecture where transactional business operations (such as appointment booking, confirmation, rescheduling, status change, and cancellation) publish strongly-typed, immutable domain events.

### Domain Event Hierarchy

| Event Type | Trigger | Target Channels | Recipients |
|---|---|---|---|
| `APPOINTMENT_CREATED` | Patient submits booking request | `IN_APP`, `EMAIL` | Patient & Doctor |
| `APPOINTMENT_CONFIRMED` | Doctor confirms appointment | `IN_APP`, `EMAIL` | Patient & Doctor |
| `APPOINTMENT_CANCELLED` | Patient or Doctor cancels | `IN_APP`, `EMAIL` | Patient & Doctor |
| `APPOINTMENT_RESCHEDULED`| Date or time updated | `IN_APP`, `EMAIL` | Patient & Doctor |
| `APPOINTMENT_STATUS_CHANGED`| Status transitions (e.g. In-Progress) | `IN_APP` | Patient |
| `APPOINTMENT_REMINDER_DUE`| Scheduler detects 24h window | `IN_APP`, `EMAIL` | Patient & Doctor |

### Event Payload Schema

```json
{
  "eventId": "evt-7a1b2c3d-4e5f",
  "eventType": "APPOINTMENT_CONFIRMED",
  "aggregateId": "6543210fedcba09876543210",
  "patientId": "pat-001",
  "doctorId": "doc-002",
  "occurredAt": "2026-11-22T04:30:00Z",
  "payloadVersion": 1,
  "metadata": {
    "date": "2026-11-23",
    "time": "10:30 AM",
    "patientName": "Kamal Perera",
    "doctorName": "Dr. Sunil Fernando",
    "status": "CONFIRMED"
  }
}
```

---

## 2. Decoupling and Fail-Safe Enqueueing

Business methods in `AppointmentServiceImpl` perform the primary database transaction first. Upon successful persistence, the domain event is emitted to `AppointmentEventPublisher`, which converts the event into deduplicated outbox records.

If any delivery channel (SMTP server down, client disconnected from WebSocket) encounters an error, the primary appointment transaction remains completely intact. Delivery is asynchronously retried by the background outbox worker.
