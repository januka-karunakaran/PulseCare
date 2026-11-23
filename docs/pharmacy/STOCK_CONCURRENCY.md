# Stock Concurrency & Atomic Decrement Design

## Concurrency Guarantee
To ensure high-throughput correctness without locks, PulseCare employs atomic conditional updates at the document level:

```java
Query query = Query.query(
    Criteria.where("id").is(batch.getId())
            .and("status").is(BatchStatus.AVAILABLE)
            .and("expiryDate").gt(Instant.now())
            .and("quantityAvailable").gte(allocQty)
);
Update update = new Update()
        .inc("quantityAvailable", -allocQty)
        .set("updatedAt", Instant.now());

UpdateResult updateResult = mongoTemplate.updateFirst(query, update, PharmacyStockBatch.class);
```

## Invariants Maintained
1. **0 Negative Inventory:** `quantityAvailable` can never drop below zero.
2. **No Dirty Reads:** Conditional criteria ensures only non-expired, available lots are modified.
3. **Deterministic Collision Handling:** When modified count is 0, system throws `InsufficientStockException` with code `STOCK_ALLOCATION_CONFLICT` or re-attempts next FEFO lot.
4. **Verified via Testcontainers:** Tested with 20 concurrent threads contesting stock.
