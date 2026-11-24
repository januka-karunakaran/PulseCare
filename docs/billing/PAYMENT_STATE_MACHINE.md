# Payment State Machine & Payment Channels

## 1. Supported Payment Methods
1. `CASH`: Front desk / Cashier counter collection.
2. `CARD_TERMINAL`: Point-of-Sale (POS) physical card terminal with external authorization code.
3. `BANK_TRANSFER`: Direct wire or EFT settlement with bank slip reference.
4. `INSURANCE_SETTLEMENT`: Verified third-party medical insurance guarantee / claim voucher.

## 2. Payment Lifecycle States
- `PENDING`: Payment recorded, awaiting physical or external clearance.
- `CONFIRMED`: Cashier or financial system confirms receipt of funds. Ledger updated.
- `FAILED`: Settlement failed or check bounced.
- `CANCELLED`: Voided prior to clearance.
- `PARTIALLY_REFUNDED`: Reversal of a portion of collected funds.
- `REFUNDED`: Full reimbursement of the payment back to the payer.

## 3. Receipt Generation
Upon confirmation of any payment in `CONFIRMED` status, an official payment receipt is generated with unique receipt number (e.g. `PAY-2026-000001`), recording:
- Payer identity
- Invoice number
- Payment channel and external reference
- Exact payment amount and timestamp
- Cashier user ID
- Printable and downloadable official PDF receipt
