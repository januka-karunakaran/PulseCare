# Hospital Bed Allocation & Discharge Concurrency

## 1. Problem Statement
The baseline implementation in `BedServiceImpl.java` performed bed booking using an unprotected read-then-write pattern:
```java
Bed bed = getBedById(booking.getBedId());
if (!"AVAILABLE".equals(bed.getStatus())) {
    throw new RuntimeException("Bed is not available");
}
bed.setStatus("OCCUPIED");
bedRepository.save(bed);
booking.setStatus("ACTIVE");
return bedBookingRepository.save(booking);
```
Consequences of this design:
- Two simultaneous ward admission requests for Bed 101 both read `status == AVAILABLE`.
- Both modify the status to `OCCUPIED` and call `save()`.
- Two `BedBooking` records are created for the same physical hospital bed, causing clinical ward conflicts.
- During discharge, separate `save()` operations on booking and bed allowed race conditions where a delayed discharge could overwrite a newly admitted patient's bed status.

---

## 2. Atomic Conditional Bed Allocation
To eliminate over-allocation, `BedServiceImpl.bookBed` uses MongoDB's atomic conditional update:

```java
Query claimQuery = Query.query(Criteria.where("_id").is(booking.getBedId()).and("status").is("AVAILABLE"));
Update claimUpdate = new Update().set("status", "OCCUPIED");
Bed claimedBed = mongoTemplate.findAndModify(
        claimQuery, 
        claimUpdate,
        FindAndModifyOptions.options().returnNew(true), 
        Bed.class
);

if (claimedBed == null) {
    throw new BedAlreadyOccupiedException("This bed has already been allocated. Please select another bed.");
}
```

### Invariants:
1. **Single Winner:** In MongoDB, a document write lock ensures that out of 20 concurrent allocation requests for the same bed, exactly ONE satisfies `{ status: "AVAILABLE" }` and transitions it to `"OCCUPIED"`. The remaining 19 return `null` and receive HTTP 409 Conflict (`BED_ALREADY_OCCUPIED`).
2. **Explicit Linkage:** The bed document stores `currentBookingId = savedBooking.getId()`.
3. **Compensating Rollback:** If creating the `BedBooking` fails, the bed status is conditionally rolled back from `OCCUPIED` to `AVAILABLE`.

---

## 3. Safe Discharge & Bed Release
Discharging a patient is a two-phase atomic process:

### Phase 1: Conditional Discharge on BedBooking
```java
Query dischargeQuery = Query.query(Criteria.where("_id").is(bookingId).and("status").is("ACTIVE"));
Update dischargeUpdate = new Update()
        .set("status", "DISCHARGED")
        .set("dischargeDate", LocalDate.now().toString());

BedBooking dischargedBooking = mongoTemplate.findAndModify(
        dischargeQuery, 
        dischargeUpdate,
        FindAndModifyOptions.options().returnNew(true), 
        BedBooking.class
);

if (dischargedBooking == null) {
    throw new InvalidBookingTransitionException("Bed booking is not active or has already been discharged");
}
```
- Only an `ACTIVE` booking can be discharged.
- Repeated calls to discharge the same booking return HTTP 409 Conflict with code `INVALID_BOOKING_TRANSITION`.

### Phase 2: Bed Release
```java
Query bedReleaseQuery = Query.query(Criteria.where("_id").is(dischargedBooking.getBedId()).and("status").is("OCCUPIED"));
Update bedReleaseUpdate = new Update()
        .set("status", "AVAILABLE")
        .unset("currentBookingId");

mongoTemplate.updateFirst(bedReleaseQuery, bedReleaseUpdate, Bed.class);
```
- Releasing the bed checks `{ status: "OCCUPIED" }` and unsets `currentBookingId`.
- Even under concurrent discharge and re-allocation, the bed is released only once and cannot overwrite an active allocation.
