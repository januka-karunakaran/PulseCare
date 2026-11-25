# Production Index Strategy

To maintain sub-100ms response times as hospital data scales, the following MongoDB indexes are maintained:

1. **Appointments**:
   - `appt_status_date_idx`: `{'status': 1, 'appointmentDate': 1}`
   - `appt_doc_date_idx`: `{'doctorId': 1, 'appointmentDate': 1}`
2. **Invoices**:
   - `inv_status_created_idx`: `{'status': 1, 'createdAt': -1}`
   - `inv_pat_issued_idx`: `{'patientId': 1, 'issuedAt': -1}`
3. **Payments**:
   - `billing_payments`: `{'status': 1, 'confirmedAt': -1}`
4. **Laboratory**:
   - `lab_results`: `{'workflowStatus': 1, 'releasedAt': -1}`
5. **Pharmacy**:
   - `pharm_batch_status_exp_idx`: `{'status': 1, 'expiryDate': 1}`
