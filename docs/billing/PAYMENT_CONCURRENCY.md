# Payment Concurrency & Race-Condition Prevention

## 1. Concurrency Problem Statement
In busy hospital environments, multiple cashiers or automated payment listeners might attempt to post payments against the same invoice simultaneously. If handled with naive read-then-write logic, race conditions can result in:
- Double deductions
- Negative outstanding balances
- Total paid exceeding total invoiced (unauthorized overpayment)

## 2. Atomic Conditional Write Guarantee
PulseCare 2.0 solves this problem with single-roundtrip atomic conditional updates via Spring Data `MongoTemplate`:

```java
Query query = Query.query(
    Criteria.where("id").is(invoice.getId())
            .and("status").in(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID)
            .and("outstandingAmount").gte(paymentAmount)
);

Update update = new Update()
        .inc("paidAmount", paymentAmount)
        .inc("outstandingAmount", paymentAmount.negate())
        .set("updatedAt", Instant.now());

UpdateResult result = mongoTemplate.updateFirst(query, update, Invoice.class);
if (result.getModifiedCount() == 0) {
    throw new PaymentConflictException(
        "PAYMENT_CONCURRENCY_CONFLICT",
        "Unable to allocate payment. The invoice status has changed or outstanding balance is less than the requested payment."
    );
}
```

## 3. Guarantees
1. **Zero Overpayment**: MongoDB guarantees that `outstandingAmount >= paymentAmount` at the moment of modification.
2. **Zero Negative Balance**: The outstanding amount cannot drop below zero.
3. **P0 Verification**: Tested and verified under 20 simultaneous threads competing for balance allocation in `BillingConcurrencyMongoIntegrationTest`.
