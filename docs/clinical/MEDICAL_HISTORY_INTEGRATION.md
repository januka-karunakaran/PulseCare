# Medical History Timeline Integration

## Unified Timeline Architecture

The medical history endpoint (`/api/clinical/patients/{id}/timeline` and `/api/patients/{id}/history`) merges:
1. **Appointments:** Scheduled, completed, or cancelled visits.
2. **Prescriptions:** Medicines prescribed by doctors.
3. **Lab Results:** Diagnostic laboratory test outcomes.
4. **Clinical Encounters:** Finalized encounters, SOAP documentation, diagnosis, and attending clinician details.

### Security Invariants
- When accessed by a **PATIENT**, unreleased drafts (`DRAFT`, `IN_PROGRESS`) are strictly omitted from the returned timeline.
- Timestamps are normalized using ISO-8601 UTC instants (`YYYY-MM-DD`).
- Bounded querying prevents unbounded JVM memory allocation.
