# Analytics Test Execution Report

| Test Class | Suite Type | Tests Executed | Passed | Failed | Errors | Skipped |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `AnalyticsServiceTest` | Unit / Mockito | 5 | 5 | 0 | 0 | 0 |
| `AnalyticsAuthorizationSecurityTest` | Security / Access Control | 4 | 4 | 0 | 0 | 0 |
| **Total Analytics Suite** | Unit & Security | **9** | **9** | **0** | **0** | **0** |

## Key Scenarios Verified:
- **`testExecutiveOverview_Admin_Success`**: Verified patient counts, active doctor filtering, bed occupancy percentage math, appointment completion rates, and period filtering.
- **`testExecutiveOverview_Patient_AccessDenied`**: Enforced that unprivileged users (e.g. `PATIENT`) cannot query executive dashboards.
- **`testAppointmentAnalytics_DoctorScope`**: Enforced that physicians receive only their own scoped appointment statistics.
- **`testBillingAnalytics_ExactBigDecimalPrecision`**: Verified exact two-decimal half-up monetary arithmetic for billed totals, discounts, taxes, collections, and receivables.
- **`testFinancialReconciliation_DetectsAnomaly`**: Verified automated detection of invoices marked `PAID` with non-zero outstanding balances.
- **`AnalyticsAuthorizationSecurityTest`**: Verified cross-role boundary enforcement (e.g., `LAB_TECH` denied billing analytics, `PHARMACIST` denied doctor workload, and unauthorized export attempts blocked).
