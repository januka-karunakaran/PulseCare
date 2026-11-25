package com.hospital.backend.service;

import com.hospital.backend.dto.analytics.*;

public interface AnalyticsService {
    ExecutiveOverviewDTO getExecutiveOverview(AnalyticsFilterRequest filter);
    AppointmentAnalyticsDTO getAppointmentAnalytics(AnalyticsFilterRequest filter);
    DoctorWorkloadDTO getDoctorWorkload(String doctorId, AnalyticsFilterRequest filter);
    PatientOperationsDTO getPatientOperations(AnalyticsFilterRequest filter);
    BedUtilizationDTO getBedUtilization();
    LaboratoryAnalyticsDTO getLaboratoryAnalytics(AnalyticsFilterRequest filter);
    PharmacyAnalyticsDTO getPharmacyAnalytics(AnalyticsFilterRequest filter);
    BillingFinancialAnalyticsDTO getBillingAnalytics(AnalyticsFilterRequest filter);
    FinancialReconciliationDashboardDTO getFinancialReconciliation();
    byte[] exportReport(ReportExportRequest request);
}
