package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.ReportExportRequest;

public interface ReportExportService {
    byte[] generateReport(ReportExportRequest request, Object reportData);
}
