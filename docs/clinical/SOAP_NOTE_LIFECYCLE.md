# SOAP Note Lifecycle & Immutability Rules

## 1. Documentation Structure
- **S (Subjective):** Patient reported symptoms, history of present illness.
- **O (Objective):** Physical exam findings, linked vital signs snapshot.
- **A (Assessment):** Clinician diagnosis text, diagnosis status (`PROVISIONAL`, `CONFIRMED`, `DIFFERENTIAL`), clinical rationale.
- **P (Plan):** Treatment plan, tests requested, follow-up instructions, follow-up date.

## 2. Integrity & Amendment Model
1. **Draft Phase:** Clinician can save iterative drafts with `@Version` optimistic locking to prevent concurrent overwrite.
2. **Signature:** Clinician signs with confirmation modal. Note state changes to `SIGNED`. `signedAt` and `signedByDoctorName` recorded.
3. **Immutability Guarantee:** Direct modification endpoints reject updates to signed notes (`HTTP 409 SIGNED_NOTE_IMMUTABLE`).
4. **Traceable Amendments:** When new findings arrive (e.g. post-visit lab result), clinician submits `/amend` with:
   - `amendmentReason` (Mandatory justification)
   - Updated/supplemental SOAP fields
   - New `ClinicalNoteRevision` created with `revisionNumber = N + 1`
   - Revision history displays chronologically with reason and author.
