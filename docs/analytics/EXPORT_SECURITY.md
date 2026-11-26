# Export Security & Formula Injection Protections

1. **CSV Formula Injection Sanitization**:
   - Every text field exported to CSV is evaluated for spreadsheet formula triggers (`=`, `+`, `-`, `@`, `\t`, `\r`).
   - Fields starting with formula prefixes (including leading whitespace) are prepended with a single quote (`'`), neutralizing macro execution in Microsoft Excel, Google Sheets, and LibreOffice Calc.
   - Fields containing commas, double quotes, or internal whitespace are properly enclosed in double quotes with internal quotes escaped (`""`).

2. **Multi-Page PDF Security & Layout Integrity**:
   - Structured multi-page tabular layout with automated page breaks, repeated column headers, and page numbering.
   - String sanitization cleans non-ASCII and control characters that trigger PDFBox `WinAnsiEncoding` exceptions.
   - Long text fields are bounded and truncated with ellipsis to prevent column overlap or layout clipping.

3. **Data Minimization & Aggregation**:
   - Executive, financial, and operational reports output strictly aggregated statistics, counts, and non-PHI summaries rather than raw medical charts or sensitive patient identifiers.

4. **Audit Logging & Access Control**:
   - Every report export emits an immutable `ClinicalAuditAction.REPORT_EXPORTED` event via `ClinicalAuditService`, recording actor, timestamp, format, and report type.
   - Role-based `@PreAuthorize` guards prevent unauthorized export attempts across all roles.
