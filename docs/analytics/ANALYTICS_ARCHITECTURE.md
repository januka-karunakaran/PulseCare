# Analytics Architecture & Aggregation Design

## 1. Design Principles
1. **Read-Only Operation**: No mutations of medical records, appointments, inventory, or financial documents occur during reporting.
2. **Database-Side Processing**: Heavy grouping, summing, filtering, and sorting occur via MongoDB aggregation pipelines (`$match`, `$group`, `$project`, `$sort`) avoiding full collection loading into JVM memory.
3. **Role-Based Access Control**:
   - `ADMIN`: Comprehensive hospital operations, bed management, executive KPIs, and financial reconciliation.
   - `DOCTOR`: Scoped personal workload, appointment statistics, and clinical encounters.
   - `LAB_TECH`: Laboratory order queues, specimen metrics, and turnaround times.
   - `PHARMACIST`: Pharmacy stock health, low stock, near-expiry batches, and dispensing metrics.
   - `BILLING_STAFF`: Billed charges, payment collections, aging, and reconciliation.
4. **Export Safety**: Formula injection protection (`=`, `+`, `-`, `@`) on CSV generation, explicit `Content-Disposition`, and audit logging of all export operations.
