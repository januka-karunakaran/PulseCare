# Pharmacy Stock Reconciliation & Movement Auditing

## Purpose & Scope
Stock reconciliation verifies that physical warehouse/dispensary quantities match digital ledger totals and detects shrinkage, damage, lot expiration, or recording errors.

## Ledger Invariant
The current quantity of any batch $B$ at time $T$ must exactly satisfy:

$$\text{Balance}(B, T) = \text{Received}(B) + \sum \Delta \text{Movements}(B, t \le T)$$

## Movement Types
1. `STOCK_RECEIVED`: Physical inventory intake (+).
2. `STOCK_DISPENSED`: Dispensation to patients (-).
3. `STOCK_ADJUSTED`: Audit correction, damage write-off (+ or -).
4. `STOCK_QUARANTINED`: Isolation of recalled or returned goods.
5. `STOCK_REVERSED`: Cancellation of prior dispensation.
6. `STOCK_WRITTEN_OFF`: Expired or contaminated disposal (-).
