# Pharmacy Subsystem Index Migration Plan

## Managed Collections & Indexes

### 1. `pharmacy_medicines`
- `medicineCode_1` (Unique): Ensures no duplicate SKU/drug code.
- `pharm_med_active_name_idx` (`{'active': 1, 'genericName': 1}`): Fast search and catalog listing.
- `pharm_med_generic_strength_form_idx` (`{'genericName': 1, 'strength': 1, 'dosageForm': 1}`): Clinical uniqueness check.

### 2. `pharmacy_stock_batches`
- `pharm_batch_fefo_idx` (`{'medicineId': 1, 'expiryDate': 1, 'status': 1}`): Crucial for high-performance FEFO lot allocation.
- `pharm_batch_med_num_idx` (`{'medicineId': 1, 'batchNumber': 1}`): Lot traceability per medicine.
- `pharm_batch_status_exp_idx` (`{'status': 1, 'expiryDate': 1}`): Expiration monitoring query optimization.

### 3. `pharmacy_dispenses`
- `dispenseNumber_1` (Unique): Traceable dispensing ID.
- `idempotencyKey_1` (Unique, Sparse): Idempotent dispensation replay safety.
- `pharm_disp_patient_date_idx` (`{'patientId': 1, 'dispensedAt': -1}`): Patient medication history timeline.
- `pharm_disp_presc_date_idx` (`{'prescriptionId': 1, 'createdAt': -1}`): Prescribing audit tracking.

### 4. `pharmacy_stock_movements`
- `pharm_mov_batch_date_idx` (`{'batchId': 1, 'occurredAt': -1}`): Lot audit ledger.
- `pharm_mov_med_date_idx` (`{'medicineId': 1, 'occurredAt': -1}`): Medicine audit ledger.
- `pharm_mov_ref_idx` (`{'referenceType': 1, 'referenceId': 1}`): Transaction lookup.

## Migration Strategy
Indexes are declared with Spring Data `@CompoundIndex` / `@Indexed`. For production deployments with replica sets, create indexes in the background:
```javascript
db.pharmacy_stock_batches.createIndex(
  { "medicineId": 1, "expiryDate": 1, "status": 1 },
  { name: "pharm_batch_fefo_idx", background: true }
);
```
