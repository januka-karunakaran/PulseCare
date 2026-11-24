# Financial Ledger and Daily Reconciliation

## 1. Double-Entry Append-Only Journal
Every monetary transaction generates an immutable journal entry in `billing_ledger_entries`:
- `CHARGE`: Invoice issuance (+Accounts Receivable).
- `PAYMENT`: Cash/card/bank collection (-Accounts Receivable, +Cash/Bank Asset).
- `REFUND`: Disbursement to patient (+Accounts Receivable, -Cash/Bank Asset).
- `CREDIT_NOTE`: Concession or correction (-Accounts Receivable).
- `ADJUSTMENT`: Reconciliation variance adjustment.

## 2. Daily Reconciliation Formula
At any cutoff timestamp, the accounting ledger enforces balance integrity:
$$\text{Closing A/R} = \text{Opening A/R} + \text{Total Invoiced Charges} - \text{Total Collections} - \text{Total Credit Notes} + \text{Total Refunds}$$

$$\text{Net Revenue} = \text{Gross Collections} - \text{Total Refunds}$$

Collections are segregated by physical channel:
$$\text{Total Collections} = \text{Cash Desk} + \text{Card Terminals} + \text{Bank Wire} + \text{Insurance Settled}$$
Any imbalance triggers an automatic audit flag in the reconciliation report.
