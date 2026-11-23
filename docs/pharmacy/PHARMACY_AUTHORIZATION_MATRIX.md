# Pharmacy Authorization Matrix

| Operation | Endpoint | ADMIN | PHARMACIST | DOCTOR | PATIENT |
|---|---|:---:|:---:|:---:|:---:|
| View Medicine Catalog | `GET /api/pharmacy/medicines/**` | Yes | Yes | Yes | Yes |
| Manage Medicine Catalog | `POST/PUT/DELETE /api/pharmacy/medicines/**` | Yes | Yes | No | No |
| Receive Stock / Batches | `POST /api/pharmacy/inventory/receive` | Yes | Yes | No | No |
| Adjust Stock | `POST /api/pharmacy/inventory/adjust` | Yes | Yes | No | No |
| View Prescription Queue | `GET /api/pharmacy/queue/**` | Yes | Yes | No | No |
| Dispense Medication | `POST /api/pharmacy/dispense` | Yes | Yes | No | No |
| Reverse Dispensation | `POST /api/pharmacy/reverse` | Yes | Yes | No | No |
| View Single Dispense | `GET /api/pharmacy/dispense/{id}` | Yes | Yes | Yes (Assigned) | Yes (Owner) |
| View Patient Dispenses | `GET /api/pharmacy/patients/{id}/dispenses` | Yes | Yes | Yes (Assigned) | Yes (Owner) |
| Stock Movement Ledger | `GET /api/pharmacy/medicines/{id}/movements` | Yes | Yes | No | No |
