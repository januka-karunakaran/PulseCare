# Refunds and Credit Notes

## 1. Credit Notes vs. Cash Refunds
- **Credit Note (`BillingCreditNote`)**:
  - Issued to adjust invoice amounts prior to or after payment due to concessions, billing disputes, or negotiated discounts.
  - Decreases invoice outstanding amount and records a `CREDIT_NOTE` ledger entry.
- **Refund (`BillingRefund`)**:
  - Involves actual disbursement of funds back to the patient for a previously confirmed payment.
  - Linked to specific `BillingPayment` ID.
  - Restores the corresponding unpaid balance on the invoice (shifting status back to `PARTIALLY_PAID` or `ISSUED`).
  - Records a `REFUND` ledger entry.

## 2. Separation from Pharmacy Stock
Refunding medication fees never automatically unquarantines or returns dispensed pharmaceuticals into active stock. Medication returns must be physically inspected, batch-verified, and quarantined by an authorized pharmacist under Phase 02.6 safety protocols before inventory is ever adjusted.
