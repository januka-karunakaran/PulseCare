# MongoDB Aggregation & Indexing Strategy

## Aggregation Pipelines

### 1. Appointment Status Breakdown & Metrics
- **Pipeline**:
  1. `$match`: `{ appointmentDate: { $gte: start, $lte: end }, ...doctorIdFilter }`
  2. `$group`: `{ _id: "$status", count: { $sum: 1 } }`
- **Benefits**: Replaces in-memory `.findAll()` / JVM stream filtering with a single indexed database-side pipeline, supporting both canonical `Doctor.id` and legacy `Doctor.userId`.

### 2. Bed Occupancy & Status Grouping
- **Pipeline**:
  1. `$group`: `{ _id: "$status", count: { $sum: 1 } }`
- **Benefits**: Replaces `bedRepository.findAll()` with an in-database count per status (`OCCUPIED`, `AVAILABLE`, `MAINTENANCE`, etc.), executing in $< 2\text{ ms}$.

### 3. Financial Reconciliation & Missing Ledger Detection
- **Pipeline**:
  1. `$match`: `{ status: "CONFIRMED" }` (limited to recent 100 entries)
  2. `$lookup`: `{ from: "billing_ledger_entries", localField: "_id", foreignField: "paymentId", as: "ledgers" }`
  3. `$match`: `{ "ledgers.0": { $exists: false } }`
  4. `$limit`: 50
- **Benefits**: Eliminates N+1 database queries that formerly queried `ledgerEntryRepository.countByPaymentId()` per payment inside a loop.

### 4. Daily Revenue & Collections
- Bounded queries on `invoices` (`issuedAt`) and `billing_payments` (`confirmedAt`) grouped by business date in timezone `Asia/Colombo`.

### 5. Laboratory Turnaround Time (TAT)
- `$match`: `{ workflowStatus: "RELEASED", releasedAt: { $gte: start, $lte: end }, measuredAt: { $exists: true } }`
- `$project`: `{ elapsedMinutes: { $divide: [{ $subtract: ["$releasedAt", "$measuredAt"] }, 60000] } }`
- `$group`: `{ _id: null, avgTat: { $avg: "$elapsedMinutes" }, count: { $sum: 1 } }`

---

## Recommended Compound Indexes
- `appointments`: `{'appointmentDate': 1, 'status': 1, 'doctorId': 1}`
- `invoices`: `{'issuedAt': -1, 'status': 1}`
- `billing_payments`: `{'confirmedAt': -1, 'status': 1}`
- `billing_ledger_entries`: `{'paymentId': 1}`
- `lab_results`: `{'workflowStatus': 1, 'releasedAt': -1}`
- `lab_specimens`: `{'collectionStatus': 1, 'rejectedAt': -1}`
- `pharmacy_stock_batches`: `{'status': 1, 'expiryDate': 1, 'medicineId': 1}`
