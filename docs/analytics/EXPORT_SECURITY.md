# Export Security & Formula Injection Protections

1. **CSV Formula Injection Sanitization**:
   - Every text field exported to CSV is checked for spreadsheet formula prefixes (`=`, `+`, `-`, `@`, `\t`, `\r`).
   - Prefixing characters are escaped with a leading single quote (`'`), neutralizing automatic macro execution in Microsoft Excel, Google Sheets, and LibreOffice Calc.
2. **Data Minimization & Aggregation**:
   - Executive and administrative exports output aggregated metrics and counts rather than raw patient charts or sensitive health identifiers.
3. **Audit Logging**:
   - Every report export emits a `ClinicalAuditAction.REPORT_EXPORTED` event via `ClinicalAuditService`, recording actor, timestamp, format, and report type.
4. **Cache & Transport Security**:
   - Export endpoints send `Cache-Control: no-cache, no-store, must-revalidate` to prevent caching of financial or operational metrics.
