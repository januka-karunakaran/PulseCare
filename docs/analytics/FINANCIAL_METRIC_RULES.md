# Financial Metric Rules & Accounting Semantics

1. **Revenue Recognition vs Cash Collection**:
   - Invoiced amounts do NOT represent cash received. Only payments with status `CONFIRMED` constitute collected funds.
   - Unpaid invoices are tracked separately under Outstanding Receivables.
2. **Void Invoices Exclusion**:
   - Invoices marked `VOID` are completely excluded from gross billed revenue and accounts receivable.
3. **Refund Accounting**:
   - Refunds deduct from net financial collections and restore corresponding invoice outstanding balances without creating ledger discrepancies.
4. **Monetary Precision**:
   - All arithmetic uses `BigDecimal` with half-up rounding to two decimal places (`RoundingMode.HALF_UP`), preventing floating-point inaccuracies.
5. **Reconciliation Exceptions**:
   - Any invoice marked `PAID` with remaining balance > 0 or confirmed payment lacking an audit ledger entry is surfaced directly on the reconciliation dashboard.
