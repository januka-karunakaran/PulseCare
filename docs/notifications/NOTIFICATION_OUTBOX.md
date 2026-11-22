# MongoDB Notification Outbox Pattern

## 1. Outbox Entity & Compound Indexes

The `notification_outbox` MongoDB collection tracks every notification destined for external or internal dispatch.

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

## 2. Atomic Lease Protocol & Multi-Node Safety

To guarantee safe operation in clustered or multi-instance deployments without distributed lock systems:

1. **Atomic Acquisition (`findAndModify`)**:
   ```javascript
   db.notification_outbox.findAndModify({
     query: {
       $or: [
         { status: { $in: ["PENDING", "RETRY"] }, nextAttemptAt: { $lte: ISODate() } },
         { status: "PROCESSING", claimUntil: { $lt: ISODate() } }
       ]
     },
     update: {
       $set: {
         status: "PROCESSING",
         claimedBy: "worker-inst-1",
         claimUntil: ISODate(now + 30 seconds),
         updatedAt: ISODate()
       }
     },
     new: true
   })
   ```
2. **Lease Recovery**: If a worker node crashes mid-dispatch, its 30-second lease expires. The next available worker automatically reclaims the record and retries delivery.
3. **Exponential Backoff**: Transient errors trigger exponential backoff:
   $$\text{backoffSeconds} = \min(2^{\text{attempt}} \times 5 + \text{jitter}, 3600)$$
4. **Dead-Letter State (`FAILED`)**: After 5 attempts, records transition to `FAILED` with explicit `lastErrorCode` and `lastErrorMessage`.
