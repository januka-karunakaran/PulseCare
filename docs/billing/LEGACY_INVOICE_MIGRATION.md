# Legacy Invoice Migration and Backward Compatibility

## 1. Monetary BSON Representation & Type Safety
In PulseCare Feature 02.7, all financial amounts in MongoDB are mapped to native BSON `Decimal128` using `@Field(targetType = FieldType.DECIMAL128)`:
- `Invoice`: `subtotal`, `discount`, `tax`, `totalAmount`, `paidAmount`, `outstandingAmount`, and line items `quantity`, `unitPrice`, `lineSubtotal`, `discountAmount`, `taxAmount`, `total`.
- `BillingPayment`: `amount`, `refundableAmount`, `refundedAmount`.
- `BillingRefund`: `amount`.
- `BillingLedgerEntry`: `amount`.
- `BillingCharge`: `quantity`, `unitPrice`, `lineSubtotal`, `taxSnapshot`, `totalAmount`.
- `BillingServiceCatalog`: `unitPrice`.
- `BillingCreditNote`: `amount`.

This guarantees:
1. Exact IEEE 754-2008 128-bit decimal floating-point representation in MongoDB.
2. Numeric compatibility with MongoDB `$inc`, `$gte`, and aggregation operators (preventing `WriteError{code=14, message='Cannot increment with non-numeric argument'}`).
3. Automatic conversion between Java `BigDecimal` (scale 2, `HALF_UP`) and BSON `Decimal128`.

## 2. Legacy Compatibility Layer
Feature 02.7 provides complete backward compatibility for legacy endpoints and documents:
- Existing `GET /api/invoices` and `POST /api/invoices` continue to function without disruption.
- `Invoice.java` includes backward-compatible `totalAmount(Double)` builder overloads that convert `Double` values to `BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP)`.
- Legacy status strings (`"UNPAID"`, `"PENDING"`) map automatically to `InvoiceStatus.ISSUED`, while `"PAID"` maps to `InvoiceStatus.PAID`.
- Documents saved with legacy BSON types (`Double`, `String`, `Int32`) are transparently mapped by Spring Data MongoDB to `BigDecimal` upon read without silent truncation.

## 3. Migration Protocol for Existing Databases (Dry-Run & Production)
When deploying Feature 02.7 to environments with existing invoices containing `Double` or `String` values, run the following MongoDB Shell dry-run and execution migration script:

```javascript
// mongosh migration script: migrate_legacy_invoices.js
// DRY-RUN MODE: Set dryRun = true to inspect without modifying
const dryRun = false;

db.invoices.find({}).forEach(doc => {
    let updates = {};
    let needsUpdate = false;

    // 1. Convert totalAmount to Decimal128 if not already
    if (doc.totalAmount !== undefined && !(doc.totalAmount instanceof NumberDecimal)) {
        updates.totalAmount = NumberDecimal(doc.totalAmount.toString());
        needsUpdate = true;
    }

    // 2. Set paidAmount default
    if (doc.paidAmount === undefined) {
        updates.paidAmount = doc.status === "PAID" ? updates.totalAmount || NumberDecimal(doc.totalAmount.toString()) : NumberDecimal("0.00");
        needsUpdate = true;
    } else if (!(doc.paidAmount instanceof NumberDecimal)) {
        updates.paidAmount = NumberDecimal(doc.paidAmount.toString());
        needsUpdate = true;
    }

    // 3. Set outstandingAmount default
    if (doc.outstandingAmount === undefined) {
        let total = updates.totalAmount || NumberDecimal(doc.totalAmount.toString());
        let paid = updates.paidAmount || (doc.paidAmount ? NumberDecimal(doc.paidAmount.toString()) : NumberDecimal("0.00"));
        // compute remainder
        updates.outstandingAmount = doc.status === "PAID" ? NumberDecimal("0.00") : total;
        needsUpdate = true;
    } else if (!(doc.outstandingAmount instanceof NumberDecimal)) {
        updates.outstandingAmount = NumberDecimal(doc.outstandingAmount.toString());
        needsUpdate = true;
    }

    if (needsUpdate) {
        if (dryRun) {
            print(`[DRY-RUN] Would update invoice ${doc._id} with: ${JSON.stringify(updates)}`);
        } else {
            db.invoices.updateOne({ _id: doc._id }, { $set: updates });
            print(`[APPLIED] Updated invoice ${doc._id}`);
        }
    }
});
```

## 4. Concurrency & Integrity Guarantees
- Concurrency operations on invoices strictly use BSON `Decimal128` arguments for `$inc` and `$gte`.
- Optimistic locking `@Version` collisions are avoided on concurrent payments by executing atomic conditional operations via `MongoTemplate.updateFirst`.
