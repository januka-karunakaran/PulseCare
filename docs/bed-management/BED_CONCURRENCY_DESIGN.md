# Bed Concurrency Design & Stress Verification

## 1. Concurrency Controls
1. **Atomic Conditional Check & Claim**: Uses `findAndModify` checking `{ _id: bedId, status: "AVAILABLE", active: { $ne: false } }`.
2. **Multi-Document Replica Set Transaction**: Encompasses bed state change, admission record persistence, and occupancy event appending.
3. **Partial Unique Index on Active Patient Admissions**: Enforces at database level that a patient cannot hold multiple active admissions simultaneously.

## 2. Concurrency Stress Evidence
In `BedAdmissionLifecycleMongoIntegrationTest.testMandatoryConcurrencyScenario_20RequestsCompetingFor1Bed`:
- 20 concurrent threads fired in burst via `CountDownLatch`.
- Result: Exactly 1 success, exactly 19 `BedAlreadyOccupiedException` conflict responses.
- Database Invariant: Exactly 1 active booking, exactly 1 occupied bed, 0 orphan records.
