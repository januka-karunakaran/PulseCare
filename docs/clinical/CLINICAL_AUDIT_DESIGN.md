# Clinical Audit Trail Design

## Collection: `clinical_audit_logs`

### Logged Actions
- `CLINICAL_RECORD_VIEWED`: Patient or doctor viewed encounter or timeline.
- `CLINICAL_RECORD_CREATED`: Encounter or vitals recorded.
- `CLINICAL_RECORD_UPDATED`: Draft note or clinical profile updated.
- `CLINICAL_RECORD_SIGNED`: Encounter signed and finalized.
- `CLINICAL_RECORD_AMENDED`: Traceable addendum appended.
- `CLINICAL_ACCESS_DENIED`: Unauthorized access attempt blocked.

### Privacy & PHI Protection
1. **No PHI in Logs:** Consultation note narratives, subjective descriptions, and diagnoses are never written to audit logs.
2. **Correlation ID:** UUID tracks user request context across layers.
3. **Append-Only:** No update or delete endpoints exist for audit records.
