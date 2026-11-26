# PulseCare 2.0 - Feature 03.1: Hospital Bed Admission, Transfer, Discharge & Occupancy History

## 1. Executive Summary
Feature 03.1 elevates PulseCare from basic bed booking into an enterprise-grade Inpatient Bed Management and Historical Occupancy System. It provides full ward structures, atomic replica-set database transactions, safe multi-step patient transfers, structured clinical discharge clearances, turnover cleaning management, and an append-only event log enabling true interval-based historical occupancy analytics.

## 2. Key Architecture Invariants
- **Atomic Concurrency Guarantee**: Bed allocation relies on atomic conditional MongoDB updates (`findAndModify`) backed by multi-document transactions. When multiple concurrent admission or transfer requests target the same bed, exactly one succeeds and all others receive HTTP 409 Conflict.
- **Append-Only Occupancy History**: Every state transition (Admit, Transfer, Vacate, Discharge, Turnover, Maintenance) writes an immutable record to `bed_occupancy_events` with server-side UTC timestamps, sequence numbers, and actor identities.
- **Interval-Based Historical Analytics**: Historical occupancy is computed from true `[occupiedAt, vacatedAt)` interval overlaps against query windows, preventing snapshot misrepresentation and boundary errors.
- **Fail-Safe Turnover**: A bed vacated by discharge or transfer enters `CLEANING` status and cannot be re-allocated until explicitly sanitized and marked `AVAILABLE`.
- **Decoupled Billing & Notifications**: Medically necessary discharges always finalize even if external billing charge registration or outbox notifications experience transient network issues.
