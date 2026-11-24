# Payment Concurrency & Financial State Integrity

## 1. Concurrency Problem Statement
In busy hospital environments, multiple cashiers or automated payment listeners might attempt to post payments against the same invoice simultaneously. If handled with naive read-then-write logic, race conditions can result in:
- Double deductions
- Negative outstanding balances
- Total paid exceeding total invoiced (unauthorized overpayment)
- Stale invoice status overwrites (e.g. `PAID` overwritten back to `PARTIALLY_PAID` by a lagging thread)

## 2. Atomic Conditional Write Guarantee
PulseCare 2.0 solves this problem with single-roundtrip atomic conditional updates via Spring Data `MongoTemplate` using exact BSON `Decimal128` arguments:

```java
org.bson.types.Decimal128 decPayAmount = new org.bson.types.Decimal128(payAmount);
org.bson.types.Decimal128 decNegPayAmount = new org.bson.types.Decimal128(payAmount.negate());

Query query = Query.query(
    Criteria.where("_id").is(invoice.getId())
            .and("status").in(List.of(InvoiceStatus.ISSUED.name(), InvoiceStatus.PARTIALLY_PAID.name()))
            .and("outstandingAmount").gte(decPayAmount)
);

Update update = new Update()
        .inc("paidAmount", decPayAmount)
        .inc("outstandingAmount", decNegPayAmount)
        .set("paymentMethod", request.getMethod())
        .set("updatedAt", Instant.now());

UpdateResult result = mongoTemplate.updateFirst(query, update, Invoice.class);
if (result.getModifiedCount() == 0) {
    throw new PaymentConflictException(
        "CONCURRENT_PAYMENT_CONFLICT",
        "Concurrent balance update conflict on invoice " + invoice.getInvoiceNumber() + ". Please retry."
    );
}
```

## 3. Invoice Status Race Resolution
To prevent slower threads from downgrading a fully settled invoice (`PAID`) back to `PARTIALLY_PAID`:
1. If `outstandingAmount <= 0`, status transitions atomically to `PAID`:
   `Query: where("_id").is(invoiceId).and("outstandingAmount").lte(0)` -> `set("status", PAID)`
2. If `outstandingAmount > 0`, status transitions to `PARTIALLY_PAID` **ONLY IF** current status is `ISSUED`:
   `Query: where("_id").is(invoiceId).and("status").is(ISSUED).and("outstandingAmount").gt(0)` -> `set("status", PARTIALLY_PAID)`
   Lagging threads processing when status was intermediate cannot overwrite `PAID`.
3. Guard condition ensures zero outstanding balance can NEVER remain `PARTIALLY_PAID`.

## 4. Refund Concurrency Protection
To prevent simultaneous refund requests from exceeding the collected payment balance:
1. `BillingPayment` tracks `refundableAmount` and `refundedAmount` as BSON `Decimal128`.
2. Atomic conditional decrement is enforced:
   ```java
   Query refQuery = Query.query(
       Criteria.where("_id").is(payment.getId())
           .and("status").in(List.of(PaymentStatus.CONFIRMED.name(), PaymentStatus.PARTIALLY_REFUNDED.name()))
           .and("refundableAmount").gte(decRefund)
   );
   Update refUpdate = new Update()
       .inc("refundableAmount", decNegRefund)
       .inc("refundedAmount", decRefund)
       .set("updatedAt", Instant.now());
   ```
3. If concurrent refund requests exceed remaining refundable balance, MongoDB rejects subsequent requests with `PaymentConflictException`.

## 5. Guarantees
1. **Zero Overpayment**: MongoDB guarantees that `outstandingAmount >= paymentAmount` at the moment of modification.
2. **Zero Negative Balance**: The outstanding amount cannot drop below zero.
3. **Zero Over-Refund**: Cumulative refunds are bounded by `refundableAmount >= decRefund`.
4. **P0 Verification**: Verified under 20 simultaneous threads in `BillingConcurrencyMongoIntegrationTest` (TEST A through TEST F).
