# Clinical Authorization Matrix

| Role | Start Encounter | Edit Draft | Sign Note | Amend Note | View Draft Note | View Finalized Note | Record Vitals | Modify Clinical Profile |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **PATIENT** | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ✅ Permitted (Own only) | ❌ Denied (403) | ❌ Read-only (Own only) |
| **DOCTOR (Assigned)** | ✅ Permitted | ✅ Permitted | ✅ Permitted | ✅ Permitted | ✅ Permitted | ✅ Permitted | ✅ Permitted | ✅ Permitted (With clinical relationship) |
| **DOCTOR (Unrelated)** | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) | ❌ Denied (403) |
| **ADMIN** | ✅ Operational | ✅ Operational | ❌ Denied (Doctor only) | ❌ Denied (Doctor only) | ✅ Operational | ✅ Operational | ✅ Operational | ✅ Operational |

### Enforcement Mechanics
- Method-level `@PreAuthorize` on controllers.
- Domain checks in `AuthorizationService.canAccessEncounter()`, `checkEncounterSigning()`, `canModifyClinicalProfile()`.
- Identity resolution mapping `Doctor.userId` and `Patient.userId` prevents IDOR.
