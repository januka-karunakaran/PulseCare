# Medication Dispensing Workflow

## End-to-End Clinical Flow
1. **Physician Prescribes:** Doctor prescribes medication during patient consultation.
2. **Pharmacy Queue Ingestion:** Prescription appears in `/pharmacy/prescriptions` with active stock status indicator.
3. **Pharmacist Review:** Licensed Pharmacist inspects dosage, strength, and catalog availability.
4. **FEFO Allocation:** System determines eligible batches sorted by expiration date ascending (`expiryDate ASC`).
5. **Atomic Stock Decrement:** Stock is deducted atomically using `mongoTemplate.updateFirst` with conditional checking (`quantityAvailable >= requested`).
6. **Ledger Record:** Each allocation creates a `PharmacyStockMovement` row with type `STOCK_DISPENSED`.
7. **Dispense Finalization:** `PharmacyDispense` record is persisted with status `DISPENSED` (or `PARTIALLY_DISPENSED`).
8. **Patient Medical History Update:** Finalized dispense appears immediately in the patient's medical timeline as `MEDICATION_DISPENSED`.
9. **Patient Notification:** In-app notification sent to patient.
