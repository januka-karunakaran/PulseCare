# Medication Returns and Dispense Reversals

## Safety & Compliance Policy
Medications returned by patients cannot be automatically returned to saleable stock due to risks of improper storage, tampering, and chain-of-custody breaks.

## Reversal Protocol
1. **Trigger:** Initiated by Pharmacist upon authorized return or clinical error reversal.
2. **Status Transition:** Original `PharmacyDispense` moves to status `REVERSED`.
3. **Quarantine Movement:** System records a `PharmacyStockMovement` of type `STOCK_QUARANTINED` for each allocated batch.
4. **Physical Quarantine:** The physical medication is transferred to the hospital's hazardous/quarantined disposal holding area.
5. **Audit Trail:** Audited under `ClinicalAuditAction.PHARMACY_DISPENSE_REVERSED`.
