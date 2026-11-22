# Notification & Scheduling Test Report

## 1. Overview of Test Coverage

All 99 unit, security, and scheduling tests across the backend test suite executed and passed with 100% success.

| Test Class | Category | Tests Run | Failures | Errors | Result |
|---|---|---|---|---|---|
| `BackendApplicationTests` | Context Bootstrap | 1 | 0 | 0 | PASSED |
| `AppointmentReminderSchedulerTest` | Unit / Scheduling | 8 | 0 | 0 | PASSED |
| `TokenCleanupSchedulerTest` | Unit / Scheduling | 3 | 0 | 0 | PASSED |
| `NotificationSecurityTest` | Security / Access Control | 10 | 0 | 0 | PASSED |
| `NotificationOutboxTest` | Unit / Outbox Processor | 8 | 0 | 0 | PASSED |
| `AccessControlSecurityTest` | Security / Access Control | 10 | 0 | 0 | PASSED |
| `AuthorizationServiceTest` | Security / Access Control | 14 | 0 | 0 | PASSED |
| `RegistrationSecurityTest` | Security / Access Control | 4 | 0 | 0 | PASSED |
| `TokenLifecycleSecurityTest` | Security / Access Control | 9 | 0 | 0 | PASSED |
| `AppointmentServiceTest` | Service / Business Logic | 8 | 0 | 0 | PASSED |
| `AuthServiceTest` | Service / Business Logic | 2 | 0 | 0 | PASSED |
| `BedServiceTest` | Service / Business Logic | 6 | 0 | 0 | PASSED |
| `SearchServiceTest` | Service / Business Logic | 12 | 0 | 0 | PASSED |
| `HealthControllerTest` | Controller / Endpoint | 4 | 0 | 0 | PASSED |
| `PrescriptionControllerTest` | Controller / Endpoint | 4 | 0 | 0 | PASSED |
| **Total Non-Integration Tests** | | **99** | **0** | **0** | **100% PASS** |

---

## 2. Key Scenarios Validated

1. **Spring ApplicationContext Bootstrap**:
   `BackendApplicationTests` loads the complete Spring Boot 3.5.6 context in 16.2s, verifying all beans (including `OutboxProcessor`, `AppointmentReminderScheduler`, `TokenCleanupScheduler`, `MailService`, `WebSocketConfig`) are properly autowired without circular dependencies or proxy type mismatches.

2. **WebSocket Strict Destination Validation**:
   - `testWebSocketSubscribeCanonicalQueueSucceeds`: Canonical `/user/queue/notifications` allowed.
   - `testWebSocketSubscribeUnauthenticatedThrowsAccessDenied`: Unauthenticated access rejected.
   - `testWebSocketSubscribeAnotherUserQueueThrowsAccessDenied`: Arbitrary user queue access rejected.
   - `testWebSocketSubscribeSubstringUserThrowsAccessDenied`: Substring spoofing rejected.
   - `testWebSocketClientSendToProtectedBrokerThrowsAccessDenied`: Client `SEND` to `/topic/**` rejected.
   - `testWebSocketClientSendToUserDestinationThrowsAccessDenied`: Client `SEND` to `/user/**` rejected.

3. **In-App Outbox Idempotency**:
   - `testDeliverInAppNotificationIdempotentWhenAlreadyPersisted`: When a notification with matching `sourceOutboxId` already exists, no duplicate is created and the user's `isRead` state is strictly preserved.
   - `testWorkerCrashAfterInAppPersistenceDoesNotCreateDuplicateNotificationOnRetry`: Full MongoDB integration test validating that a simulated worker crash does not cause duplicate notifications on lease recovery.

4. **Appointment Reminder Correctness**:
   - `testMissingAppointmentTimeReturnsNullWithoutDefaultingToNineAm`: Null/blank times return null without defaulting to 09:00 AM.
   - `testClockInjectionForDeterministicEvaluation`: Evaluates 24h window accurately with injected clock.
   - `testProcessItemSuppressesReminderIfAppointmentCancelledInDatabase`: Cancels reminder outbox item if appointment is cancelled in DB before worker dispatch.
