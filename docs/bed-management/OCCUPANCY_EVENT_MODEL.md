# Immutable Occupancy Event Model

## 1. Event Taxonomy
- `ADMISSION_STARTED`: Inpatient admission registered.
- `BED_OCCUPIED`: Bed marked occupied by patient.
- `TRANSFER_STARTED`: Transfer initiated.
- `BED_VACATED`: Patient departed bed.
- `TRANSFER_COMPLETED`: Transfer finalized.
- `DISCHARGE_COMPLETED`: Patient discharged.
- `BED_TURNOVER_STARTED`: Bed entered cleaning status.
- `BED_AVAILABLE`: Bed cleaning verified, ready for new patient.
- `BED_MAINTENANCE_STARTED`: Bed taken offline for repairs.
- `BED_MAINTENANCE_ENDED`: Repairs finished, bed returned to service.

## 2. Immutability & Ordering
Events are strictly append-only. Sequence numbers are maintained per admission to ensure deterministic chronological replay.
