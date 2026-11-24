# PulseCare 2.0 - Feature 02.7 Engineering Report: Hospital Billing, Invoicing & Payment Integrity

## 1. Executive Summary
Feature 02.7 modernizes the financial core of PulseCare 2.0, upgrading the legacy invoice model into a double-entry financial ledger and multi-channel billing operations engine. The implementation eliminates floating-point monetary calculations, guarantees zero negative balance and zero overpayment via atomic MongoDB operations, establishes strict RBAC (`BILLING_STAFF`), provides immutable ledger journals, enables Apache PDFBox document generation (Invoices, Receipts, Credit Notes), and preserves full backward compatibility with legacy endpoints.

## 2. Key Objectives & Milestones Achieved
1. **Financial Data Precision**: All monetary values transitioned to Java `BigDecimal` with 2-decimal scale (`RoundingMode.HALF_UP`). Floating-point arithmetic completely eliminated.
2. **Atomic Concurrency Protection**: High-concurrency payment processing guaranteed by atomic conditional updates (`mongoTemplate.updateFirst`), ensuring no overpayments or negative outstanding balances even under simultaneous cashier transactions.
3. **Billable Charge Registry**: Clinical events (Consultations, Diagnostics, Prescriptions, Bed stays) are captured in `billing_charges` with compound unique indexing `{'sourceType': 1, 'sourceId': 1, 'sourceLineId': 1}` preventing duplicate charges.
4. **Comprehensive Lifecycle**: Multi-state invoice management (`DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `VOID`) with audit logging and durable Notification Outbox alerts.
5. **Role-Based Access Control**: Added `BILLING_STAFF` with least privileges across billing management, catalog configuration, payment collections, and refund administration. Prevented IDOR attacks on patient invoices.
6. **Immutable Financial Ledger**: Append-only journal in `billing_ledger_entries` recording all charges, payments, refunds, and credit notes for comprehensive auditability.
7. **Daily Reconciliation**: End-of-day journal and payment channel breakdown (Cash desk, POS Card, Bank transfer, Insurance settlement).
8. **Document Generation**: High-fidelity PDF generation using Apache PDFBox for patient invoices, official receipts, and credit notes.
9. **Front-End Workspaces**: Responsive portals for Billing Staff dashboard, Service Price Catalog, Daily Reconciliation, Admin Invoices, and Patient Portal Bills & Payments.

## 3. Technology Stack Alignment
- **Backend**: Java 17, Spring Boot 3.5.6, Spring Security, Spring Data MongoDB.
- **Frontend**: Next.js 16 (App Router), React 19, Tailwind CSS.
- **Document Engine**: Apache PDFBox 3.0.4.
- **Database**: MongoDB 7.0 with replica set transactional capabilities and compound unique indexes.
- **CI/CD**: GitHub Actions unified pipeline (`.github/workflows/ci.yml`).
