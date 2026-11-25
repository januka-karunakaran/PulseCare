# Data Source to Metric Mapping

| Domain System | Source MongoDB Collection | Primary Fields | Key Metrics Derived |
| :--- | :--- | :--- | :--- |
| **Patients** | `patients` | `_id`, `gender`, `bloodGroup` | Patient counts, demographics |
| **Doctors** | `doctors` | `_id`, `status`, `specialization` | Doctor counts, workload summaries |
| **Appointments** | `appointments` | `appointmentDate`, `doctorId`, `status` | Daily trends, status distribution, doctor load |
| **Beds** | `beds` | `status`, `type`, `ward` | Bed occupancy, ward breakdown |
| **Clinical Encounters** | `clinical_encounters` | `doctorId`, `status`, `visitStartedAt` | Encounters started, signed, drafts |
| **Laboratory** | `lab_orders`, `lab_results` | `status`, `priority`, `workflowStatus`, `releasedAt` | Turnaround time, order volumes, interpretations |
| **Pharmacy** | `pharmacy_medicines`, `pharmacy_stock_batches`, `pharmacy_dispenses` | `reorderLevel`, `quantityAvailable`, `expiryDate`, `status` | Low-stock count, near-expiry, dispenses |
| **Billing** | `invoices`, `billing_payments`, `billing_refunds`, `billing_ledger_entries` | `totalAmount`, `paidAmount`, `outstandingAmount`, `method`, `confirmedAt` | Billed amount, collections, aging, anomalies |
