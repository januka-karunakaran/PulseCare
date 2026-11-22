# MongoDB Notification Outbox Pattern

## 1. Outbox Entity & Compound Indexes

The `notification_outbox` collection tracks every notification destined for external or internal dispatch.

### Schema Definition
- `id` (String / ObjectId): Unique outbox record ID.
- `eventId` (String): Source domain event UUID.
- `eventType` (String): e.g., `APPOINTMENT_REMINDER_DUE`.
- `aggregateId` (String): Reference appointment or prescription ID.
- `recipientUserId` (String): Canonical User ID of recipient.
- `recipientEmail` (String): Target email address.
- `recipientRole` (String): `PATIENT` or `DOCTOR`.
- `channel` (Enum): `IN_APP` or `EMAIL`.
- `status` (Enum): `PENDING`, `PROCESSING`, `DELIVERED`, `RETRY`, `FAILED`, `CANCELLED`.
- `attemptCount` (int): Number of processing attempts executed.
- `maxAttempts` (int): Default 5.
- `nextAttemptAt` (Instant): Next scheduled execution instant for retries.
- `claimUntil` (Instant): Lease expiration timestamp.
- `claimedBy` (String): Unique identifier of claiming worker instance.
- `deduplicationKey` (String, Unique): Natural deterministic idempotent key.

### Optimized Compound Indexes
1. `{'status': 1, 'nextAttemptAt': 1}`: Fast query for eligible `PENDING` and `RETRY` records.
2. `{'status': 1, 'claimUntil': 1}`: Fast detection of expired leases for automatic recovery.
3. `{'aggregateId': 1, 'status': 1}`: Immediate batch cancellation when an appointment is cancelled.
4. `{'recipientUserId': 1, 'createdAt': -1}`: Recipient history lookup.
5. `{'deduplicationKey': 1}`: Enforces database-level duplicate prevention.

---

## 2. In-App Notification Idempotency with `sourceOutboxId`

To prevent duplicate in-app notifications if a worker dies after writing to `notifications` but before marking the outbox item `DELIVERED`:

1. `Notification` entity contains `@Indexed(unique = true, sparse = true) private String sourceOutboxId;`.
2. Before creating a notification, `OutboxProcessor` queries `findBySourceOutboxId(item.getId())`.
3. If an existing record is found:
   - It reuses the saved notification.
   - It never overwrites or resets `isRead` or `readAt` timestamps.
4. If concurrent workers attempt insertion, MongoDB unique index constraint catches `DuplicateKeyException` and safely falls back to reading the existing notification.

---

## 3. Atomic Lease Protocol & Worker Protection

1. **Atomic Acquisition (`findAndModify`)**:
   Claims ready `PENDING`/`RETRY` records or expired `PROCESSING` records (`claimUntil < now`) with a 30-second lease.
2. **Lease Guarded Updates**:
   When transitioning an outbox item to `DELIVERED`:
   ```java
   Query query = new Query(Criteria.where("_id").is(item.getId())
           .and("status").is(OutboxStatus.PROCESSING.name())
           .and("claimedBy").is(workerId));
   Update update = new Update()
           .set("status", OutboxStatus.DELIVERED.name())
           .set("deliveredAt", now)
           .set("updatedAt", now);
   long modified = mongoTemplate.updateFirst(query, update, NotificationOutbox.class).getModifiedCount();
   ```
   If a worker's lease expired and another worker stole the record, `modified == 0`, preventing stale workers from overwriting newer states.
3. **Exponential Backoff**: Transient errors trigger exponential backoff:
   $$\text{backoffSeconds} = \min(2^{\text{attempt}} \times 5 + \text{jitter}, 3600)$$
4. **Dead-Letter State (`FAILED`)**: After 5 attempts, records transition to `FAILED` with explicit `lastErrorCode` and `lastErrorMessage`.
