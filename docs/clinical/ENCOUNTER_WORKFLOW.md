# Clinical Encounter Workflow

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Doctor starts consultation from Appointment
    DRAFT --> IN_PROGRESS : Resumed / Vitals & SOAP drafted
    IN_PROGRESS --> SIGNED : Doctor Signs & Finalizes
    SIGNED --> AMENDED : Doctor appends Addendum with Reason
    AMENDED --> AMENDED : Subsequent Addenda
    DRAFT --> CANCELLED : Appointment Cancelled
    IN_PROGRESS --> CANCELLED : Appointment Cancelled
```

### Transition Invariants
1. **Starting Encounter:** Requires non-cancelled appointment. Assigned doctor only. If active encounter exists, resumes existing draft.
2. **Draft Updates:** Only allowed in `DRAFT` or `IN_PROGRESS`. Attempting to update `SIGNED` encounter throws `CLINICAL_005 (SIGNED_NOTE_IMMUTABLE)`.
3. **Signing:** Requires assigned clinician. Freezes note as Revision 1. Generates outbox notification for patient.
4. **Amending:** Requires signed baseline. Incrementing `revisionNumber` to 2+. Mandatory `amendmentReason`. Previous revisions preserved intact.
5. **Patient Visibility:** Patients can only retrieve `SIGNED` and `AMENDED` records. Attempts to read drafts throw `403 Forbidden`.
