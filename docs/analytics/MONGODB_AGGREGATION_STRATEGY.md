# MongoDB Aggregation & Indexing Strategy

## Aggregation Pipelines
- **Daily Appointment Trends**:
  `$match` on date boundaries $\rightarrow$ `$group` by `appointmentDate` summing completions/cancellations $\rightarrow$ `$sort` by date ascending.
- **Daily Revenue & Collections**:
  Bounded queries on `invoices` (`issuedAt`) and `billing_payments` (`confirmedAt`) grouped by business date in timezone `Asia/Colombo`.
- **Laboratory Turnaround Time**:
  `$match` on `workflowStatus: RELEASED` with valid `measuredAt` and `releasedAt` $\rightarrow$ `$project` elapsed minutes $\rightarrow$ `$group` average TAT.

## Recommended Compound Indexes
- `appointments`: `{'appointmentDate': 1, 'status': 1, 'doctorId': 1}`
- `invoices`: `{'issuedAt': -1, 'status': 1}`
- `billing_payments`: `{'confirmedAt': -1, 'status': 1}`
- `lab_results`: `{'workflowStatus': 1, 'releasedAt': -1}`
- `pharmacy_stock_batches`: `{'status': 1, 'expiryDate': 1}`
