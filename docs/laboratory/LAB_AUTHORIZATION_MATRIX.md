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
| Verify Result | YES | YES (Licensed Doctor) | NO | NO |
| Release Result to Patient | YES | YES (Licensed Doctor) | NO | NO |
| Correct Released Result | YES | YES (Licensed Doctor) | NO | NO |
| View Draft / Entered Result | YES | YES | YES | NO |
| View Released Result | YES | YES | YES | YES (Own Record) |
| Download PDF Report | YES | YES | YES | YES (Released Only) |
