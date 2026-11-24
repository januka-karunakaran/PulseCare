# Billing Role-Based Access Control (RBAC) Matrix

| Operational Capability | ADMIN | BILLING_STAFF | DOCTOR | PATIENT | LAB_TECH | PHARMACIST |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| View Billing Dashboard | Yes | Yes | No | No | No | No |
| Configure Service Catalog & Prices | Yes | Yes | No | No | No | No |
| Create Manual Billable Charges | Yes | Yes | No | No | No | No |
| Draft and Issue Invoices | Yes | Yes | No | No | No | No |
| Void Unpaid Invoices | Yes | Yes | No | No | No | No |
| Record Patient Payments | Yes | Yes | No | No | No | No |
| Process Refunds & Credit Notes | Yes | Yes | No | No | No | No |
| View Daily Financial Reconciliation | Yes | Yes | No | No | No | No |
| Download Invoice / Receipt PDF | Yes | Yes | No | Own Only | No | No |
| View Own Invoices & Payment History | Yes | Yes | No | Own Only | No | No |

## IDOR Prevention
Any access to `/api/billing/invoices/{id}` or `/api/billing/invoices/{id}/pdf` by a user with role `PATIENT` verifies ownership via `authorizationService.checkInvoiceAccess(invoice)`. If the invoice does not belong to the authenticated patient, HTTP 403 Forbidden is immediately raised and logged to the clinical audit trail.
