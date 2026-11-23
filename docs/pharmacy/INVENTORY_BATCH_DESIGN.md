# Inventory & Batch Management Design

## Document Schema: `pharmacy_stock_batches`

| Field | Type | Description |
|---|---|---|
| `id` | String | Primary Key |
| `medicineId` | String | Foreign key to `PharmacyMedicine` |
| `batchNumber` | String | Manufacturer Lot/Batch ID |
| `supplierReference`| String | Purchase Order / Supplier Invoice Reference |
| `receivedAt` | Instant | Timestamp of physical receipt |
| `manufacturedAt` | Instant | Manufacturing date |
| `expiryDate` | Instant | Lot expiration date |
| `quantityReceived` | Double | Original received quantity |
| `quantityAvailable`| Double | Current unallocated available quantity |
| `quantityReserved` | Double | Stock reserved for in-progress dispenses |
| `stockUnit` | String | Counting unit |
| `storageLocation` | String | Shelf, room, or bin indicator |
| `status` | BatchStatus| `AVAILABLE`, `QUARANTINED`, `EXPIRED`, `DEPLETED` |
| `idempotencyKey` | String | Prevent duplicate receipt operations |

## FEFO Compound Index
- `pharm_batch_fefo_idx`: `{'medicineId': 1, 'expiryDate': 1, 'status': 1}`
Queries selecting available stock sort by `expiryDate ASC` and filter `status == AVAILABLE` and `expiryDate > now`.
