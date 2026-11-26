# Bed Stay Billing Integration

## 1. Authoritative Daily Rates
Bed daily rates are maintained in authoritative `Decimal128` (`dailyRate`), with legacy `Double` fallback (`pricePerDay`).

## 2. Charge Registration Lifecycle
Upon administrative discharge:
1. Stay duration is computed between `admittedAt` and `dischargedAt`.
2. Billable days are calculated using ceiling 24-hour blocks (minimum 1 day).
3. `BillingService.registerClinicalCharge` is invoked with:
   - `sourceType = ChargeSourceType.BED_STAY`
   - `sourceId = admission.getId()`
   - `sourceLineId = "STAY_" + admission.getId() + "_DAYS_" + billableDays`
   - `serviceCode = "BED_STAY_STANDARD"`
   - `quantity = billableDays`
4. Idempotency is guaranteed by compound unique index `billing_chg_source_uniq_idx` on `{sourceType, sourceId, sourceLineId}`.
5. Discharge is decoupled: if billing registration fails, an alert is logged for reconciliation and patient discharge commits without rollback.
