# Key Performance Indicator (KPI) Definitions

| KPI Category | Metric Name | Definition & Derivation | Time Semantics | Fallback / Completeness Status |
| :--- | :--- | :--- | :--- | :--- |
| **Patients** | Total Registered Patients | Total documents in `patients` collection | Lifetime count | Lifetime database count via `patientRepository.count()`. |
| **Patients** | Newly Registered Patients | Patients registered within selected date boundaries using server-generated `createdAt` (UTC) | Bounded by `createdAt` in UTC range | If all records have timestamps: `status = COMPLETE`. If partial: `status = PARTIAL_HISTORICAL_DATA`. If none: returns `null` with `status = UNAVAILABLE`. |
| **Patients** | Patients with Consultations | Distinct unique patients with either a completed appointment or a finalized clinical encounter (`SIGNED` / `AMENDED`) | Filtered by `appointmentDate` / `visitStartedAt` in period | Resolves canonical `Patient.id` and legacy `User.id` safely, deduplicating patients across both sources. |
| **Beds (Snapshot)** | Current Bed Occupancy Rate | `(Occupied Beds / Total Beds) * 100.0` | Current real-time snapshot | Aggregated database-side via MongoDB `$group` on `status`. |
| **Beds (Historical)** | Occupied Bed-Hours | $\sum \text{overlap}(\text{interval}, \text{window})$ | Period (Selected Day / 7D / 30D / Custom) | Reconstructed from immutable `bed_occupancy_events` intervals. |
| **Beds (Historical)** | Historical Occupancy Rate (%) | `(Occupied Bed-Hours / Eligible Available Bed-Hours) * 100.0` | Period | True interval-based utilization rate with zero-denominator safety. |
| **Beds (Inpatient)** | Average Length of Stay (ALOS) | $\frac{\sum \text{Stay Duration}}{\text{Total Completed Inpatient Stays}}$ | Period | Derived from `bed_bookings` `admittedAt` to `dischargedAt`. |
| **Appointments** | Completion Rate | `(Completed / Total Appointments) * 100.0` | Filtered by `appointmentDate` | Aggregated database-side via MongoDB `$group` by status (`COMPLETED`, `APPROVED`, `CANCELLED`, `PENDING`). |
| **Appointments** | Cancellation Rate | `((Cancelled + Rejected) / Total) * 100.0` | Filtered by `appointmentDate` | Aggregated database-side via MongoDB `$group` by status. |
| **Laboratory** | Turnaround Time (TAT) | `(releasedAt - measuredAt)` average in minutes | Results released in period | Computed via MongoDB aggregation on `workflowStatus: RELEASED`. |
| **Laboratory** | Pending Collection | Lab orders in `ORDERED` or `COLLECTION_PENDING` | Real-time queue count | Database count query on order workflow statuses. |
| **Laboratory** | Specimen Rejections | Count of rejected lab specimens within date boundaries | Filtered by `rejectedAt` on `lab_specimens` | Queries `lab_specimens` where `collectionStatus == REJECTED` and `rejectedAt` is in range. |
| **Pharmacy** | Catalog Active Medicines | Count of active medicine catalog entries | Real-time catalog | Filtered on `active: true` in `pharmacy_medicines`. |
| **Pharmacy** | Available Batches Count | Count of unexpired, non-quarantined batches with positive available stock | Real-time inventory check | Query on `status: AVAILABLE`, `expiryDate > now`, `quantityAvailable > 0`. |
| **Pharmacy** | Stock by Unit | Aggregated quantity available grouped by unit of measurement | Real-time inventory check | Unit-safe aggregation (`stockByUnit: Map<String, Double>`). Avoids scalar addition across incompatible unit dimensions (e.g. TABLETS + VIALS + ML). Missing units labeled `UNKNOWN`. |
| **Pharmacy** | Low Stock Items | Active catalog medicines whose available batch stock <= `reorderLevel` | Real-time inventory check | Active medicines compared against available batch sums. |
| **Pharmacy** | Near Expiry Batches | Active batches with `expiryDate <= now + 30 days` and `quantityAvailable > 0` | Real-time batch check | Bounded query on `status: AVAILABLE` and `expiryDate`. |
| **Billing** | Gross Billed Amount | Sum of `Invoice.totalAmount` for non-VOID invoices | Filtered by `issuedAt` | Bounded aggregation with exact `BigDecimal` (Decimal128) arithmetic. |
| **Billing** | Confirmed Collections | Sum of `BillingPayment.amount` for `CONFIRMED` payments | Filtered by `confirmedAt` | Bounded aggregation with exact `BigDecimal` precision. |
| **Billing** | Outstanding Receivables | Sum of `Invoice.outstandingAmount` for active bills | Filtered by `issuedAt` | Bounded aggregation on unpaid/partially paid invoices. |
| **Billing** | Net Collections | `Confirmed Collections - Total Refunds` | Standard financial calculation | Exact `BigDecimal` calculation. |
| **Reconciliation** | Total Inconsistencies Detected | True count of detected anomalies across the entire financial database | Audit timestamp snapshot | Sum of count queries for invalid paid invoices, unledgered confirmed payments, and over-refunded payments. Exposes `truncated` / `hasMore` when page is limited. |
