# Data Source to Metric Mapping

| Domain System | Source MongoDB Collection | Primary Fields | Key Metrics Derived |
| :--- | :--- | :--- | :--- |
| **Patients** | `patients` | `_id`, `createdAt`, `gender`, `bloodGroup` | Total patients, new registrations (timestamp-bounded or flagged fallback), demographics |
| **Doctors** | `doctors` | `_id`, `userId`, `status`, `specialization` | Active doctor counts, doctor-scoped workload summaries (supporting both `id` and `userId` cross-references) |
| **Appointments** | `appointments` | `appointmentDate`, `doctorId`, `status`, `patientId` | Status distribution via `$group`, completion/cancellation rates, daily trends, active patient consultations |
| **Beds** | `beds` | `status`, `type`, `ward` | Bed occupancy rate, total, occupied, available, and maintenance counts via `$group` |
| **Clinical Encounters** | `clinical_encounters` | `doctorId`, `status`, `visitStartedAt`, `patientId` | Encounters started/signed, active patients with consultations in period |
| **Laboratory** | `lab_orders`, `lab_results`, `lab_specimens` | `status`, `workflowStatus`, `releasedAt`, `measuredAt`, `collectionStatus`, `rejectedAt` | Turnaround time (TAT), order volumes, interpretations, specimen rejection rates |
| **Pharmacy** | `pharmacy_medicines`, `pharmacy_stock_batches`, `pharmacy_dispenses` | `active`, `reorderLevel`, `stockUnit`, `quantityAvailable`, `expiryDate`, `status` | Active catalog count, unit-safe batch stock (`stockByUnit`), low-stock count, near-expiry count, dispense trends |
| **Billing** | `invoices`, `billing_payments`, `billing_refunds`, `billing_ledger_entries` | `totalAmount`, `paidAmount`, `outstandingAmount`, `method`, `confirmedAt`, `paymentId` | Gross billing, confirmed collections, refunds, aging analysis, reconciliation anomalies via `$lookup` |
