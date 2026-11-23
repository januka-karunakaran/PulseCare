# Medicine Catalog Data Model

## Document Schema: `pharmacy_medicines`

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String (ObjectId) | Yes | Primary Document Identifier |
| `medicineCode` | String | Yes (Unique) | Unique SKU/Drug Code (e.g. `MED-AMOX-500`) |
| `genericName` | String | Yes | Active ingredient/generic name |
| `brandName` | String | No | Commercial trade name |
| `strength` | String | Yes | Dosage strength (e.g. `500mg`, `10mg/5mL`) |
| `dosageForm` | DosageForm | Yes | `TABLET`, `CAPSULE`, `SYRUP`, `INJECTION`, etc. |
| `dispensingUnit` | String | Yes | Individual dispensing unit (e.g. `capsule`, `vial`) |
| `packageDescription`| String | No | Packaging details (e.g. `Bottle of 100`) |
| `manufacturer` | String | No | Pharmaceutical manufacturer |
| `active` | Boolean | Yes | Catalog active status flag |
| `reorderLevel` | Double | Yes | Minimum stock threshold triggering reorder alert |
| `createdAt` | Instant | Yes | Timestamp of creation |
| `updatedAt` | Instant | Yes | Timestamp of last modification |
| `version` | Long | Yes | Optimistic locking version |

## Compound Indexes
- `pharm_med_active_name_idx`: `{'active': 1, 'genericName': 1}`
- `pharm_med_generic_strength_form_idx`: `{'genericName': 1, 'strength': 1, 'dosageForm': 1}`
