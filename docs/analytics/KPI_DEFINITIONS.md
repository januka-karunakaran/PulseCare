# Key Performance Indicator (KPI) Definitions

| KPI Category | Metric Name | Definition & Derivation | Time Semantics |
| :--- | :--- | :--- | :--- |
| **Patients** | Total Registered Patients | Total documents in `patients` collection | Lifetime count |
| **Patients** | Newly Registered Patients | Patients registered within selected date boundaries | Based on creation timestamp |
| **Beds** | Bed Occupancy Rate | `(Occupied Beds / Total Beds) * 100.0` | Current real-time snapshot |
| **Appointments** | Completion Rate | `(Completed / Total Appointments) * 100.0` | Filtered by `appointmentDate` |
| **Appointments** | Cancellation Rate | `((Cancelled + Rejected) / Total) * 100.0` | Filtered by `appointmentDate` |
| **Laboratory** | Turnaround Time (TAT) | `(releasedAt - measuredAt)` average in minutes | Results released in period |
| **Laboratory** | Pending Collection | Lab orders in `ORDERED` or `COLLECTION_PENDING` | Real-time queue count |
| **Pharmacy** | Low Stock Items | Medicines whose active batch stock <= `reorderLevel` | Real-time inventory check |
| **Pharmacy** | Near Expiry Batches | Active batches with `expiryDate <= now + 30 days` | Real-time batch check |
| **Billing** | Gross Billed Amount | Sum of `Invoice.totalAmount` for non-VOID invoices | Filtered by `issuedAt` |
| **Billing** | Confirmed Collections | Sum of `BillingPayment.amount` for `CONFIRMED` | Filtered by `confirmedAt` |
| **Billing** | Outstanding Receivables | Sum of `Invoice.outstandingAmount` for active bills | Filtered by `issuedAt` |
| **Billing** | Net Collections | `Confirmed Collections - Total Refunds` | Standard financial calculation |
