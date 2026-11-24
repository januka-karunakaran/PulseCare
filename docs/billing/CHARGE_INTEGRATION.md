# Charge Integration & Deduplication

## 1. Integration Sources
PulseCare 2.0 unifies billable event capture across all operational domains:

1. **Appointments & Consultations (`APPOINTMENT`)**:
   - Triggered when clinical encounter is signed.
   - Service Code: Configured doctor consultation tariff.
2. **Laboratory Orders (`LAB_ORDER`)**:
   - Triggered when diagnostic order is placed or verified.
   - Service Code: Lab test catalog code (e.g. `CBC`, `GLUC`, `LIPID`).
3. **Pharmacy Dispensing (`PHARMACY_DISPENSE`)**:
   - Triggered upon pharmacist dispensing confirmation.
   - Service Code: Medicine catalog ID and unit retail price.
4. **Inpatient Bed Stays (`BED_STAY`)**:
   - Daily midnight cron or discharge accounting.
   - Service Code: Bed category daily rate.
5. **Manual Charges (`MANUAL_SERVICE`)**:
   - Audited fee addition entered by Billing Staff with required clinical rationale.

## 2. Deduplication Architecture
Double-billing prevention is guaranteed by a compound unique MongoDB index:
```json
{
  "sourceType": 1,
  "sourceId": 1,
  "sourceLineId": 1
}
```
If an appointment, prescription item, or lab order attempt to register a charge again, MongoDB rejects the duplicate write with `DuplicateKeyException`.
