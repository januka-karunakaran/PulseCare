# Performance Benchmarks & Query Complexity Analysis

## Performance Benchmark Status: NOT MEASURED (Algorithmic Complexity Guaranteed)

> [!NOTE]
> Reproducible millisecond benchmarks require an isolated, hardware-dedicated benchmark harness and multi-node MongoDB replica set with pre-warmed synthetic collections. In this CI/CD and developer environment, arbitrary millisecond estimates are explicitly marked as **NOT MEASURED** to avoid unverified production performance claims.
> Instead, performance and memory safety are guaranteed through strict asymptotic query complexity boundaries, database-side aggregation pipelines, and indexed filters.

---

## Asymptotic Query Complexity & Memory Footprint

1. **Elimination of Unbounded Collection Scans**:
   - Zero `findAll()` calls on high-volume transactional collections (`appointments`, `invoices`, `billing_payments`, `beds`, `pharmacy_stock_batches`).
   - All collection queries enforce either selective indexed criteria or strict database-side pagination (`limit(100)`).

2. **MongoDB Database-Side Aggregations**:
   - **Bed Status Distribution**: Database-side `$group` on indexed `status`. Complexity: $O(N_{\text{beds}})$ processed entirely within the MongoDB engine.
   - **Appointment Status Aggregation**: Filtered on `appointmentDate` index + `$group` on `status`. Complexity: $O(M)$ where $M$ is appointments in date window.
   - **Laboratory Turnaround Time**: Bounded `$match` on `workflowStatus: RELEASED` + `$project` + `$group`. Memory footprint bounded by index on `{'workflowStatus': 1, 'releasedAt': -1}`.
   - **Financial Reconciliation Anomaly Checks**:
     - Invalid Paid Invoices: Count query + bounded find (`limit(100)`).
     - Missing Ledger Entries: Count aggregation pipeline + bounded aggregation (`limit(100)`) with `$lookup` joining on `_id` and `paymentId`. Eliminates N+1 loop latency.
     - Over-refunded Payments: Count query + bounded find (`limit(100)`).

3. **Memory Footprint & JVM Protection**:
   - Streaming PrintWriter for CSV generation.
   - Paginated multi-page PDF generation via PDFBox with streaming page creation and disposal.
   - All monetary arithmetic uses `BigDecimal` with scale 2 (`HALF_UP`) matching BSON Decimal128, eliminating floating-point memory expansion and inaccuracies.
