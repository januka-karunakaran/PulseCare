# Laboratory Authorization & Access Matrix

| Action | ADMIN | DOCTOR | LAB_TECH | PATIENT |
| :--- | :---: | :---: | :---: | :---: |
| Browse Active Catalog | YES | YES | YES | YES |
| Manage Catalog (Create/Edit/Deactivate) | YES | NO | NO | NO |
| Create Lab Order | YES | YES (Assigned/Relationship) | NO | NO |
| Cancel Lab Order | YES | YES (Assigned Doctor) | NO | NO |
| View Lab Orders | YES | YES (Assigned/Relationship) | YES (Queue) | YES (Own Non-Draft) |
| Collect Specimen | YES | YES | YES | NO |
| Receive / Reject Specimen | YES | YES | YES | NO |
| Enter Result / Draft | YES | YES | YES | NO |
| Update Result Draft | YES | YES | YES | NO |
| Verify Result (Clinical Validation) | **NO** (Non-Clinician) | **YES** (Licensed Physician) | NO | NO |
| Release Result to Patient Record | YES | YES | NO | NO |
| Correct Released Result (Amendment) | **NO** | **YES** (Licensed Physician) | NO | NO |
| View Draft / Entered Result | YES | YES | YES | NO |
| View Released Result | YES | YES | YES | YES (Own Record) |
| Download PDF Report | YES | YES | YES | YES (Released Only) |
| View Unverified Legacy Results (`null` status) | YES (Audit Review) | YES (Clinical Review) | YES | **NO** (Hidden) |

---

## Detailed Enforcement Rules
1. **Clinical Verification Boundary:** Only licensed physicians (`ROLE_DOCTOR`) have the professional and legal qualification to verify clinical results. Administrative accounts without clinical roles are strictly denied (`canVerifyLabResult() == false`).
2. **Object-Level Order Access:** In `getResultsForOrder(orderId)`, callers must pass `checkLabOrderAccess(order)`. Cross-patient access attempts throw HTTP 403 `AccessDeniedException`.
3. **Patient Result Gating:** Patients cannot retrieve drafts, entered, verified (unreleased), or legacy unverified results. Only results with `workflowStatus == RELEASED` are returned to patient callers.
