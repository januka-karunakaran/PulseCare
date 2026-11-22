# Notification & Scheduling Test Report

## 1. Overview of Test Coverage

Feature 02.3 introduces rigorous unit, concurrency, security, and integration test suites validating all components under failure, concurrency, and edge conditions.

| Test Suite | Purpose | Target Classes | Result |
|---|---|---|---|
| `NotificationOutboxTest` | Unit tests for atomic leasing, retry backoff, max attempts, in-app and email dispatch | `OutboxProcessor` | PASSED |
| `AppointmentReminderSchedulerTest` | Unit tests for 24h window calculation, Asia/Colombo timezone, past/cancelled suppression | `AppointmentReminderScheduler` | PASSED |
| `TokenCleanupSchedulerTest` | Unit tests for expired refresh & password reset token purge, idempotency, failure resilience | `TokenCleanupScheduler` | PASSED |
| `NotificationSecurityTest` | Security tests verifying ownership enforcement on delete/update and cross-tenant access rejection | `NotificationController`, `NotificationService` | PASSED |
| `NotificationOutboxMongoIntegrationTest` | Concurrency & lease recovery tests with Testcontainers MongoDB | `NotificationOutboxRepository`, `MongoTemplate` | PASSED |

---

## 2. Key Test Scenarios Validated

1. **Concurrent Atomic Claim Contention**:
   - 8 concurrent worker threads contending for 15 pending outbox items simultaneously.
   - Verified that zero items were double-claimed and every item was leased exactly once.

2. **Lease Expiration & Worker Recovery**:
   - Simulated worker node crash where item was left in `PROCESSING` with expired lease.
   - Verified a healthy worker successfully reclaims the expired lease and delivers the notification.

3. **Reminder Lookahead & Suppression**:
   - Verified appointments 24 hours away generate reminders.
   - Verified appointments > 26 hours away are suppressed.
   - Verified cancelled, completed, and past appointments are strictly suppressed.

4. **Cross-Tenant Attack Prevention**:
   - Attempted deletion of User A's notification by User B: rejected with `AccessDeniedException` (HTTP 403).
   - Attempted mark-as-read on User A's notification by User B: rejected with `AccessDeniedException` (HTTP 403).
