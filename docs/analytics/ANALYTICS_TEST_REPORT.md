# Analytics Test Execution Report

| Test Class | Suite Type | Tests Executed | Passed | Failed | Errors | Skipped |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `AnalyticsServiceTest` | Unit / Mockito | 8 | 8 | 0 | 0 | 0 |
| `AnalyticsAuthorizationSecurityTest` | Security / Access Control | 4 | 4 | 0 | 0 | 0 |
| `ReportExportServiceTest` | Export / Multi-Page PDF & CSV | 2 | 2 | 0 | 0 | 0 |
| `FinancialReconciliationMongoIntegrationTest` | MongoDB Integration (Testcontainers) | 3 | 3 | 0 | 0 | 0 |
| **Total Analytics Suite** | Unit, Security & Integration | **17** | **17** | **0** | **0** | **0** |

## Scenarios Verified:
- **`testExecutiveOverview_Admin_Success`**: Verified patient counts, active doctor filtering, bed occupancy percentage math, appointment completion rates, and period filtering.
- **`testExecutiveOverview_Patient_AccessDenied`**: Enforced that unprivileged users (`PATIENT`) cannot query executive dashboards.
- **`testAppointmentAnalytics_DoctorScope`**: Enforced that physicians receive only their own scoped appointment statistics.
- **`testBillingAnalytics_ExactBigDecimalPrecision`**: Verified exact two-decimal half-up monetary arithmetic for billed totals, discounts, taxes, collections, and receivables.
- **`testFinancialReconciliation_DetectsAnomaly`**: Verified automated detection of invoices marked `PAID` with non-zero outstanding balances and preview flags.
- **`testPatientRegistrationCompleteness`**: Verified that missing timestamps result in `UNAVAILABLE` or `PARTIAL_HISTORICAL_DATA` and never fabricate registration counts.
- **`testConsultationsAggregation_Deduplication`**: Verified that patients with both appointments and encounters are deduplicated without double counting.
- **`testPharmacyStockByUnit_DecimalPreserved`**: Verified unit-safe batch stock grouping, decimal preservation, and absence of incompatible scalar addition.
- **`testCsvExport_FormulaInjectionNeutralized`**: Verified neutralization of malicious spreadsheet formula triggers.
- **`testPdfExport_MultiPageDocument_Success`**: Verified that large datasets generate valid multi-page PDF documents without clipping.
- **`AnalyticsAuthorizationSecurityTest`**: Verified cross-role boundary enforcement (e.g. `LAB_TECH` denied billing analytics, `PHARMACIST` denied doctor workload).
- **`FinancialReconciliationMongoIntegrationTest.testConfirmedPayment_WithNormalBillingService_NotClassifiedAsMissing`**: Verified real end-to-end payment creation via `BillingService.recordPayment()` where payment and ledger entry are joined without type-mismatch false positives.
- **`FinancialReconciliationMongoIntegrationTest.testConfirmedPayment_WithoutLedgerEntry_DetectedAsMissing`**: Verified exact detection of orphan confirmed payments lacking ledger entries.
- **`FinancialReconciliationMongoIntegrationTest.testBothBsonObjectIdAndStringIds_Supported`**: Verified that both BSON `ObjectId` and `String` ID formats are matched symmetrically in the aggregation `$lookup` pipeline.
