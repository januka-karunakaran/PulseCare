# Legacy Bed Booking Migration Strategy

## 1. Invariants & Preservation
1. Existing `BedBooking` documents contain string dates (`YYYY-MM-DD`). These are preserved intact without fabricated past seconds or times.
2. New fields (`admittedAt`, `dischargedAt`, `admissionNumber`, `currentWardId`) are populated dynamically on active and future records.
3. Legacy endpoints (`POST /api/beds/book`, `POST /api/beds/discharge/{id}`) are retained and routed through the new transactional lifecycle.
4. Historical analytics accurately flags date ranges preceding event log deployment as `PARTIAL_HISTORY`.
