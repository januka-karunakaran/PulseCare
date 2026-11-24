# PulseCare 2.0 - Feature 02.7 Engineering Report: Hospital Billing, Invoicing & Payment Integrity

## 1. Executive Summary
Feature 02.7 modernizes the financial core of PulseCare 2.0, upgrading the legacy invoice model into a double-entry financial ledger and multi-channel billing operations engine. The implementation eliminates floating-point monetary calculations, guarantees zero negative balance and zero overpayment via atomic MongoDB operations, establishes strict RBAC (`BILLING_STAFF`), provides immutable ledger journals, enables Apache PDFBox document generation (Invoices, Receipts, Credit Notes), and preserves full backward compatibility with legacy endpoints.

## 2. Key Objectives & Milestones Achieved
1. **Financial Data Precision**: All monetary values transitioned to Java `BigDecimal` with 2-decimal scale (`RoundingMode.HALF_UP`) and persisted as native BSON `Decimal128` (`@Field(targetType = FieldType.DECIMAL128)`).
2. **Atomic Concurrency Protection**: High-concurrency payment processing guaranteed by atomic conditional updates (`mongoTemplate.updateFirst`), ensuring no overpayments, no negative balances, and no status regressions (`PAID` -> `PARTIALLY_PAID`).
3. **Billable Charge Registry**: Clinical events (Consultations, Diagnostics, Prescriptions, Bed stays) captured in `billing_charges` with compound unique indexing `{'sourceType': 1, 'sourceId': 1, 'sourceLineId': 1}` preventing duplicate charges.
4. **Coordinated Payment State Machine**: Strict verification states (`PENDING`, `CONFIRMED`, `PARTIALLY_REFUNDED`, `REFUNDED`). Immediate confirmation for cash desk receipts; idempotent confirmation for bank/card settlements.
5. **Refund Concurrency Control**: Atomic conditional decrement on `refundableAmount` prevents concurrent over-refunds.
6. **Role-Based Access Control**: Added `BILLING_STAFF` with least privileges across billing management, catalog configuration, payment collections, and refund administration.
7. **Immutable Financial Ledger**: Append-only journal in `billing_ledger_entries` recording all charges, payments, refunds, and credit notes.
8. **Daily Reconciliation**: End-of-day journal and payment channel breakdown (Cash desk, POS Card, Bank transfer, Insurance settlement).
9. **Document Generation**: High-fidelity PDF generation using Apache PDFBox for patient invoices, official receipts, and credit notes.
10. **Front-End Workspaces**: Responsive portals for Billing Staff dashboard, Service Price Catalog, Daily Reconciliation, Admin Invoices, and Patient Portal Bills & Payments.

## 3. Technology Stack Alignment
- **Backend**: Java 17, Spring Boot 3.5.6, Spring Security, Spring Data MongoDB.
- **Frontend**: Next.js 16 (App Router), React 19, Tailwind CSS.
- **Document Engine**: Apache PDFBox 3.0.4.
- **Database**: MongoDB 7.0 with replica set capabilities and compound unique indexes.
- **CI/CD**: GitHub Actions unified pipeline (`.github/workflows/ci.yml`).
