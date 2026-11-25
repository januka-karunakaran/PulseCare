"use client";

import { apiFetch } from "./api";
import { API_BASE } from "@/utils/constants";
import { getAccessToken } from "@/utils/auth";

export const getExecutiveOverview = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/overview${query ? `?${query}` : ""}`);
};

export const getAppointmentAnalytics = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/appointments${query ? `?${query}` : ""}`);
};

export const getMyDoctorWorkload = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/doctors/me${query ? `?${query}` : ""}`);
};

export const getDoctorWorkload = (doctorId, params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/doctors/${doctorId}${query ? `?${query}` : ""}`);
};

export const getPatientOperations = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/patients/operations${query ? `?${query}` : ""}`);
};

export const getBedUtilization = () => {
  return apiFetch("/analytics/beds");
};

export const getLaboratoryAnalytics = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/laboratory${query ? `?${query}` : ""}`);
};

export const getPharmacyAnalytics = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/pharmacy${query ? `?${query}` : ""}`);
};

export const getBillingAnalytics = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/billing${query ? `?${query}` : ""}`);
};

export const getFinancialReconciliation = () => {
  return apiFetch("/analytics/billing/reconciliation");
};

export const exportAnalyticsReport = async (exportRequest) => {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE}/analytics/reports/export`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(exportRequest),
  });

  if (!res.ok) {
    throw new Error(`Export failed with HTTP status ${res.status}`);
  }
  return res.blob();
};
