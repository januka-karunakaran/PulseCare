# Key Performance Indicator (KPI) Definitions

| KPI Category | Metric Name | Definition & Derivation | Time Semantics | Fallback / Nuance |
| :--- | :--- | :--- | :--- | :--- |
| **Patients** | Total Registered Patients | Total documents in `patients` collection | Lifetime count | Unconditional database count. |
| **Patients** | Newly Registered Patients | Patients registered within selected date boundaries | Based on `createdAt` timestamp | If `createdAt` is missing from legacy records, returns `null` with `registrationTimestampStatus = "HISTORICAL_TIMESTAMPS_NOT_AVAILABLE"`. |
| **Patients** | Patients with Consultations | Distinct patients with `COMPLETED` appointments or clinical encounters within date range | Filtered by `appointmentDate` / `visitStartedAt` | Aggregated from appointment and encounter records in selected period. |
| **Beds** | Bed Occupancy Rate | `(Occupied Beds / Total Beds) * 100.0` | Current real-time snapshot | Aggregated database-side via MongoDB `$group` on `status`. |
| **Appointments** | Completion Rate | `(Completed / Total Appointments) * 100.0` | Filtered by `appointmentDate` | Aggregated database-side via MongoDB `$group` by status (`COMPLETED`, `APPROVED`, `CANCELLED`, `PENDING`). |
| **Appointments** | Cancellation Rate | `((Cancelled + Rejected) / Total) * 100.0` | Filtered by `appointmentDate` | Aggregated database-side via MongoDB `$group` by status. |
| **Laboratory** | Turnaround Time (TAT) | `(releasedAt - measuredAt)` average in minutes | Results released in period | Computed via MongoDB aggregation on `workflowStatus: RELEASED`. |
| **Laboratory** | Pending Collection | Lab orders in `ORDERED` or `COLLECTION_PENDING` | Real-time queue count | Database count query on order workflow statuses. |
| **Laboratory** | Specimen Rejections | Count of rejected lab specimens within date boundaries | Filtered by `rejectedAt` on `lab_specimens` | Queries `lab_specimens` where `collectionStatus == REJECTED` and `rejectedAt` is in range. |
| **Pharmacy** | Low Stock Items | Active catalog medicines whose available batch stock <= `reorderLevel` | Real-time inventory check | Active medicines (`active: true`) compared against available batch sums. |
| **Pharmacy** | Near Expiry Batches | Active batches with `expiryDate <= now + 30 days` and `quantityAvailable > 0` | Real-time batch check | Bounded query on `status: AVAILABLE` and `expiryDate`. |
| **Pharmacy** | Stock by Unit | Aggregated quantity available grouped by unit of measurement | Real-time inventory check | Avoids scalar addition across mismatched units (e.g., TABLET, VIAL, ML) by presenting a `Map<String, Double>`. |
| **Billing** | Gross Billed Amount | Sum of `Invoice.totalAmount` for non-VOID invoices | Filtered by `issuedAt` | Bounded aggregation with exact `BigDecimal` (Decimal128) arithmetic. |
| **Billing** | Confirmed Collections | Sum of `BillingPayment.amount` for `CONFIRMED` payments | Filtered by `confirmedAt` | Bounded aggregation with exact `BigDecimal` precision. |
| **Billing** | Outstanding Receivables | Sum of `Invoice.outstandingAmount` for active bills | Filtered by `issuedAt` | Bounded aggregation on unpaid/partially paid invoices. |
| **Billing** | Net Collections | `Confirmed Collections - Total Refunds` | Standard financial calculation | Exact `BigDecimal` calculation. |
