# Inpatient Discharge and Turnover Workflow

## 1. Two-Phase Discharge Model
Hospital safety requires distinguishing between:
- **Clinical Clearance (`prepareDischarge`)**: Authorized physician certifies patient is medically fit for discharge.
- **Administrative Discharge (`dischargePatient`)**: Desk or ward staff finalizes administrative discharge, releases bed to turnover cleaning, and initiates billing reconciliation.

## 2. Bed Turnover State
When discharged, the bed is marked `CLEANING`. It cannot be re-allocated until hospital sanitation staff complete room cleaning and call `POST /api/beds/{id}/mark-ready`, returning it to `AVAILABLE`.
