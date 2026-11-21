# State Transition Matrix & Lifecycle Invariants

## 1. Appointment Lifecycle States

### States:
- `PENDING`: Initial state upon creation; reserves a doctor slot and holds a daily token number.
- `CONFIRMED` / `APPROVED`: Doctor or clinical admin confirmed the appointment.
- `SCHEDULED`: Alternative scheduled state (compatible with enterprise calendar sync).
- `COMPLETED`: Clinical consultation finished (terminal state).
- `CANCELLED`: Patient or doctor cancelled the appointment (terminal state; releases slot reservation).
- `REJECTED`: Doctor or admin rejected the booking (terminal state; releases slot reservation).
- `NO_SHOW`: Patient failed to appear for the scheduled appointment (terminal state).

### Transition Matrix:

| Current State | Target State | Allowed? | Required Role | Slot Reservation Action | Error on Violation |
|---|---|---|---|---|---|
| `PENDING` | `CONFIRMED` | **YES** | `DOCTOR`, `ADMIN` | Retained | N/A |
| `PENDING` | `APPROVED` | **YES** | `DOCTOR`, `ADMIN` | Retained | N/A |
| `PENDING` | `REJECTED` | **YES** | `DOCTOR`, `ADMIN` | Released | N/A |
| `PENDING` | `CANCELLED` | **YES** | `PATIENT` (owner), `DOCTOR`, `ADMIN` | Released | N/A |
| `PENDING` | `COMPLETED` | **NO** | N/A | N/A | HTTP 409 `INVALID_BOOKING_TRANSITION` |
| `CONFIRMED` | `COMPLETED` | **YES** | `DOCTOR`, `ADMIN` | Retained (Historical) | N/A |
| `CONFIRMED` | `CANCELLED` | **YES** | `PATIENT` (owner), `DOCTOR`, `ADMIN` | Released | N/A |
| `CONFIRMED` | `NO_SHOW` | **YES** | `DOCTOR`, `ADMIN` | Retained (Historical) | N/A |
| `CANCELLED` | `CANCELLED` | **YES (Idempotent)** | Any authorized | None (already released) | Idempotent 200 OK |
| `CANCELLED` | `*` (Any other) | **NO** | N/A | N/A | HTTP 409 `INVALID_BOOKING_TRANSITION` |
| `COMPLETED` | `*` (Any other) | **NO** | N/A | N/A | HTTP 409 `INVALID_BOOKING_TRANSITION` |
| `REJECTED` | `*` (Any other) | **NO** | N/A | N/A | HTTP 409 `INVALID_BOOKING_TRANSITION` |

---

## 2. Hospital Bed Booking Lifecycle States

### States:
- `ACTIVE`: Patient admitted to the bed. Bed document has `status: OCCUPIED` and `currentBookingId: <id>`.
- `DISCHARGED`: Patient discharged from the bed. Bed document has `status: AVAILABLE`.

### Transition Matrix:

| Current State | Target State | Allowed? | Required Role | Bed Document Effect | Error on Violation |
|---|---|---|---|---|---|
| `ACTIVE` | `DISCHARGED` | **YES** | `DOCTOR`, `ADMIN` | `status -> AVAILABLE`, `currentBookingId -> null` | N/A |
| `ACTIVE` | `ACTIVE` | **NO** | N/A | None | HTTP 409 `INVALID_BOOKING_TRANSITION` |
| `DISCHARGED` | `DISCHARGED` | **NO** | `DOCTOR`, `ADMIN` | None (prevents double release) | HTTP 409 `INVALID_BOOKING_TRANSITION` |
| `DISCHARGED` | `ACTIVE` | **NO** | N/A | None | HTTP 409 `INVALID_BOOKING_TRANSITION` |

---

## 3. Bed Physical States
- `AVAILABLE`: Ready for patient allocation.
- `OCCUPIED`: Currently allocated to an `ACTIVE` booking.
- `MAINTENANCE`: Bed undergoing cleaning, maintenance, or repair.

### Guard Invariant:
Attempting to manually change an `OCCUPIED` bed's status to `AVAILABLE` or `MAINTENANCE` via admin endpoints while an `ACTIVE` booking is registered throws `InvalidBookingTransitionException` ("Cannot modify bed status away from OCCUPIED while an active booking exists. Please discharge the patient first.").
Attempting to delete an `OCCUPIED` bed throws `InvalidBookingTransitionException` ("Cannot delete bed while occupied").
