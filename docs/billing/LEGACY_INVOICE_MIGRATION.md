# Legacy Invoice Migration and Backward Compatibility

## 1. Legacy Compatibility Layer
Feature 02.7 provides complete backward compatibility for legacy endpoints:
- Existing `GET /api/invoices` and `POST /api/invoices` continue to function without disruption.
- `Invoice.java` includes backward-compatible `totalAmount(Double)` builder overloads that convert `Double` values to `BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP)`.
- Legacy status strings (`"UNPAID"`, `"PENDING"`) map automatically to `InvoiceStatus.ISSUED`, while `"PAID"` maps to `InvoiceStatus.PAID`.

## 2. Migration Protocol for Existing Databases
When deploying Feature 02.7 to environments with existing invoices:
1. Existing documents without `paidAmount` default to `0.00` (or `totalAmount` if status is `PAID`).
2. Existing documents without `outstandingAmount` have their balance computed as `totalAmount - paidAmount`.
3. Unique indexes on `invoiceNumber` and compound indexes on `billing_charges` are automatically ensured by Spring Data Mongo or initialization migration scripts.
