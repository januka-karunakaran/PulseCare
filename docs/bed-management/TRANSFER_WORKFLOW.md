# Bed Transfer Workflow & Concurrency Rules

## 1. Atomic Transfer Invariant
Patient transfers must never leave a patient without a bed or cause destination double-occupancy.

### Steps in Multi-Document Transaction:
1. **Claim Destination**: Conditional update on destination bed `status == AVAILABLE`.
2. **Release Source**: Conditional update on source bed `currentBookingId == admission.getId()` -> set `status = CLEANING`.
3. **Update Admission**: Re-point `currentBedId` and `currentWardId` to destination.
4. **Persist Transfer Record**: Write to `bed_transfers`.
5. **Append Occupancy Events**:
   - `BED_VACATED` on source bed
   - `BED_OCCUPIED` on destination bed
   - `TRANSFER_COMPLETED` on admission
6. **Notification Outbox**: Enqueue `BED_TRANSFER_COMPLETED`.
