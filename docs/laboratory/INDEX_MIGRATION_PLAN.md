# Laboratory MongoDB Index & Schema Migration Plan

## 1. Indexes Created & Enforced

### 1. `lab_test_definitions`
- Unique index on `testCode`: ensures catalog code uniqueness.
- Compound index on `{'active': 1, 'category': 1}`: optimizes catalog browsing.

### 2. `lab_orders`
- Unique index on `orderNumber`: prevents duplicate order numbers.
- Compound index on `{'patientId': 1, 'orderedAt': -1}`: optimizes patient order timeline queries.
- Compound index on `{'doctorId': 1, 'orderedAt': -1}`: optimizes physician work queues.
- Compound index on `{'status': 1, 'priority': 1}`: optimizes STAT and urgent order triage.

### 3. `lab_specimens`
- Unique index on `specimenNumber`: ensures accession code uniqueness.
- Compound index on `{'labOrderId': 1, 'collectionStatus': 1}`: specimen lookup per order.

### 4. `lab_results`
- Compound index on `{'labOrderId': 1, 'testCode': 1}`: fast result retrieval per order item.
- Compound index on `{'patientId': 1, 'releasedAt': -1}`: patient portal history indexing.
- Compound index on `{'workflowStatus': 1, 'updatedAt': -1}`: verification queue indexing.

---

## 2. Legacy Privacy & Migration Policy

### Policy Guidelines
1. **Zero Automated Overwrite:** Existing records in `lab_results` with `workflowStatus == null` retain all historical data without destructive in-place migrations.
2. **Patient Privacy Shield:** Legacy unverified results are strictly excluded from patient-visible medical history and APIs until reviewed and explicitly released by qualified clinical staff.
3. **Clinical Review:** Doctors and administrators retain audit visibility into unverified legacy results for historical care continuity.
4. **Non-Destructive Dry-Run Strategy:** Future backfill migrations must operate via dry-run audit reports before applying `workflowStatus = RELEASED` to historically confirmed records.
