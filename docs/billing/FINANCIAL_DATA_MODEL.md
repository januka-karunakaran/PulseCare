# Financial Data Model

## 1. Entity Overview
PulseCare 2.0 introduces 7 dedicated MongoDB collections supporting full financial traceability:

### 1. `billing_service_catalog`
- `id`: String (ObjectId)
- `serviceCode`: String (Unique, e.g. `CNS-001`, `LAB-CBC-01`, `PHM-AMOX-500`)
- `serviceName`: String
- `category`: ServiceCategory (`CONSULTATION`, `LABORATORY`, `PHARMACY`, `INPATIENT_BED`, `PROCEDURE`, `OTHER`)
- `description`: String
- `currency`: String (Default: `LKR` or `USD`)
- `unitPrice`: BigDecimal
- `taxCategory`: String (`STANDARD`, `EXEMPT`, `REDUCED`)
- `active`: Boolean
- Indexes:
  - `serviceCode` (Unique)
  - `{'category': 1, 'active': 1}`
  - `{'active': 1, 'serviceName': 1}`

### 2. `billing_charges`
- `id`: String (ObjectId)
- `chargeNumber`: String (Unique, e.g. `CHG-1795490000000-A1B2`)
- `patientId`: String
- `sourceType`: ChargeSourceType (`APPOINTMENT`, `LAB_ORDER`, `PHARMACY_DISPENSE`, `BED_STAY`, `MANUAL_SERVICE`)
- `sourceId`: String
- `sourceLineId`: String
- `serviceCode`: String
- `descriptionSnapshot`: String
- `quantity`: BigDecimal
- `unitPrice`: BigDecimal
- `lineSubtotal`: BigDecimal
- `taxSnapshot`: BigDecimal
- `totalAmount`: BigDecimal
- `chargeStatus`: ChargeStatus (`PENDING`, `INVOICED`, `CANCELLED`)
- `invoiceId`: String
- Indexes:
  - `{'sourceType': 1, 'sourceId': 1, 'sourceLineId': 1}` (Compound Unique)
  - `{'patientId': 1, 'chargeStatus': 1, 'createdAt': -1}`
  - `{'invoiceId': 1}`

### 3. `invoices`
- `id`: String (ObjectId)
- `invoiceNumber`: String (Unique, e.g. `INV-2026-000001`)
- `patientId`: String
- `patientName`: String
- `currency`: String
- `items`: List of `InvoiceItem` (`chargeId`, `serviceCode`, `description`, `quantity`, `unitPrice`, `lineSubtotal`, `discountAmount`, `taxAmount`, `total`)
- `subtotal`: BigDecimal
- `discount`: BigDecimal
- `tax`: BigDecimal
- `totalAmount`: BigDecimal
- `paidAmount`: BigDecimal
- `outstandingAmount`: BigDecimal
- `status`: InvoiceStatus (`DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `VOID`)
- `paymentMethod`: PaymentMethod
- `issuedAt`: Instant
- `dueAt`: Instant
- `idempotencyKey`: String (Sparse Unique)
- Indexes:
  - `invoiceNumber` (Unique)
  - `{'patientId': 1, 'issuedAt': -1}`
  - `{'status': 1, 'dueAt': 1}`
  - `{'idempotencyKey': 1}` (Sparse Unique)

### 4. `billing_payments`
- `id`: String (ObjectId)
- `paymentNumber`: String (Unique, e.g. `PAY-2026-000001`)
- `invoiceId`: String
- `patientId`: String
- `amount`: BigDecimal
- `method`: PaymentMethod (`CASH`, `CARD_TERMINAL`, `BANK_TRANSFER`, `INSURANCE_SETTLEMENT`)
- `status`: PaymentStatus (`PENDING`, `CONFIRMED`, `FAILED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`)
- `externalReference`: String
- `idempotencyKey`: String (Sparse Unique)
- `receivedAt`: Instant
- `confirmedAt`: Instant
- `confirmedByUserId`: String

### 5. `billing_ledger_entries`
- `id`: String (ObjectId)
- `entryNumber`: String (Unique)
- `invoiceId`: String
- `patientId`: String
- `entryType`: LedgerEntryType (`CHARGE`, `PAYMENT`, `REFUND`, `CREDIT_NOTE`, `ADJUSTMENT`, `REVERSAL`)
- `amount`: BigDecimal
- `currency`: String
- `referenceType`: String
- `referenceId`: String
- `occurredAt`: Instant
- `actorUserId`: String

### 6. `billing_refunds`
- `id`: String (ObjectId)
- `refundNumber`: String (Unique)
- `paymentId`: String
- `invoiceId`: String
- `patientId`: String
- `amount`: BigDecimal
- `reasonCode`: String
- `approvedByUserId`: String
- `processedAt`: Instant

### 7. `billing_credit_notes`
- `id`: String (ObjectId)
- `creditNoteNumber`: String (Unique)
- `invoiceId`: String
- `patientId`: String
- `amount`: BigDecimal
- `reason`: String
- `issuedByUserId`: String
- `issuedAt`: Instant
