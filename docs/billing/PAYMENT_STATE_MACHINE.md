# Payment State Machine & Payment Channels

## 1. Supported Payment Methods
1. `CASH`: Front desk / Cashier counter collection. Immediately confirmed by authorized cashier evidence.
2. `CARD_TERMINAL`: Point-of-Sale (POS) physical card terminal with external transaction authorization code.
3. `BANK_TRANSFER`: Direct wire or EFT settlement with bank slip reference.
4. `INSURANCE_SETTLEMENT`: Verified third-party medical insurance guarantee / claim voucher.

## 2. Payment Lifecycle States & Transitions
- `PENDING`: Payment recorded, awaiting verification of funds or authorization evidence.
- `CONFIRMED`: Cashier or financial system confirms receipt of funds. Invoice balance is debited, ledger updated.
- `FAILED`: Settlement failed, invalid slip, or payment rejected.
- `CANCELLED`: Voided prior to clearance.
- `PARTIALLY_REFUNDED`: Reversal of a portion of collected funds.
- `REFUNDED`: Full reimbursement of the payment back to the payer.

### State Transition Rules:
1. `PENDING` -> `CONFIRMED`: Via `BillingService.confirmPayment(paymentId)` or immediately during authorized `CASH` collection in `recordPayment`.
2. `CONFIRMED` -> `PARTIALLY_REFUNDED`: Via `BillingService.processRefund` when `refundAmount < refundableAmount`.
3. `CONFIRMED` -> `REFUNDED`: Via `BillingService.processRefund` when `refundAmount == refundableAmount`.
4. Idempotency: Calling `confirmPayment` on an already `CONFIRMED` payment is strictly idempotent, leaves timestamps unaltered, and never debits invoice balances twice.

## 3. Atomic Financial Coordination
When a payment is confirmed:
1. Atomically decrements `Invoice.outstandingAmount` by `Decimal128` and increments `paidAmount`.
2. Sets `Payment.status = CONFIRMED`, records `confirmedByUserId` and `confirmedAt`.
3. Writes immutable entry into `billing_ledger_entries`.
4. Coordinates invoice state transition (`PAID` or `PARTIALLY_PAID`) without status regression.
5. If any step fails, financial balance mutations are aborted, leaving all records in a consistent state.

## 4. Receipt Generation
Upon confirmation in `CONFIRMED` status, an official payment receipt is generated with unique receipt number (e.g. `PAY-2026-000001`), recording:
- Payer identity
- Invoice number
- Payment channel and external reference
- Exact payment amount (BSON Decimal128) and timestamp
- Cashier user ID
- Printable and downloadable official PDF receipt
