# Report Authorization Matrix

| Role | Executive Overview | Appointment Analytics | Doctor Workload | Bed Utilization | Lab Analytics | Pharmacy Analytics | Billing Analytics | Reconciliation |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **ADMIN** | YES | YES | YES (All) | YES | YES | YES | YES | YES |
| **DOCTOR** | NO | YES (Scoped) | YES (Self) | NO | NO | NO | NO | NO |
| **LAB_TECH** | NO | NO | NO | NO | YES | NO | NO | NO |
| **PHARMACIST**| NO | NO | NO | NO | NO | YES | NO | NO |
| **BILLING_STAFF** | NO | NO | NO | NO | NO | NO | YES | YES |
| **PATIENT** | NO | NO | NO | NO | NO | NO | NO | NO |
