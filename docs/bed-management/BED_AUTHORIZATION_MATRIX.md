# Inpatient Role-Based Authorization Matrix

| Endpoint | ADMIN | DOCTOR | BED_MANAGER | PATIENT | LAB_TECH | PHARMACIST | BILLING_STAFF |
|---|---|---|---|---|---|---|---|
| `POST /api/wards` | Allow | Deny | Deny | Deny | Deny | Deny | Deny |
| `GET /api/wards` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
| `POST /api/beds` | Allow | Deny | Deny | Deny | Deny | Deny | Deny |
| `GET /api/beds` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
| `GET /api/beds/availability` | Allow | Allow | Allow | Allow | Allow | Allow | Allow |
| `POST /api/admissions` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
| `POST /api/admissions/*/transfers` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
| `POST /api/admissions/*/discharge-ready` | Allow | Allow | Deny | Deny | Deny | Deny | Deny |
| `POST /api/admissions/*/discharge` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
| `GET /api/admissions/my-history` | Deny | Deny | Deny | Allow | Deny | Deny | Deny |
| `GET /api/beds/*/occupancy-history` | Allow | Allow | Allow | Deny | Deny | Deny | Deny |
