"use client";

import { apiFetch } from "./api";

// Bed Operations
export const getAllBeds = () => apiFetch("/beds");
export const getAvailableBeds = () => apiFetch("/beds/availability");
export const getBedById = (id) => apiFetch(`/beds/${id}`);
export const createBed = (data) => apiFetch("/beds", { method: "POST", body: JSON.stringify(data) });
export const updateBed = (id, data) => apiFetch(`/beds/${id}`, { method: "PUT", body: JSON.stringify(data) });
export const deleteBed = (id) => apiFetch(`/beds/${id}`, { method: "DELETE" });

// Bed Turnover & Readiness
export const markBedCleaning = (id) => apiFetch(`/beds/${id}/mark-cleaning`, { method: "POST" });
export const markBedReady = (id) => apiFetch(`/beds/${id}/mark-ready`, { method: "POST" });
export const markBedMaintenance = (id) => apiFetch(`/beds/${id}/mark-maintenance`, { method: "POST" });
export const getBedOccupancyHistory = (id) => apiFetch(`/beds/${id}/occupancy-history`);

// Ward Operations
export const getAllWards = (activeOnly = false) => apiFetch(`/wards?activeOnly=${activeOnly}`);
export const getWardById = (id) => apiFetch(`/wards/${id}`);
export const createWard = (data) => apiFetch("/wards", { method: "POST", body: JSON.stringify(data) });
export const updateWard = (id, data) => apiFetch(`/wards/${id}`, { method: "PUT", body: JSON.stringify(data) });
export const deleteWard = (id) => apiFetch(`/wards/${id}`, { method: "DELETE" });

// Inpatient Admissions Lifecycle
export const getActiveAdmissions = () => apiFetch("/admissions");
export const getAdmissionById = (id) => apiFetch(`/admissions/${id}`);
export const getMyAdmissions = () => apiFetch("/admissions/my-history");
export const getPatientAdmissions = (patientId) => apiFetch(`/admissions/patient/${patientId}`);
export const getDoctorAdmissions = (doctorId) => apiFetch(`/admissions/doctor/${doctorId}`);
export const admitPatient = (data) => apiFetch("/admissions", { method: "POST", body: JSON.stringify(data) });
export const transferBed = (admissionId, data) => apiFetch(`/admissions/${admissionId}/transfers`, { method: "POST", body: JSON.stringify(data) });
export const prepareDischarge = (admissionId, data) => apiFetch(`/admissions/${admissionId}/discharge-ready`, { method: "POST", body: JSON.stringify(data || {}) });
export const dischargePatient = (admissionId, data) => apiFetch(`/admissions/${admissionId}/discharge`, { method: "POST", body: JSON.stringify(data || {}) });
export const getAdmissionTransfers = (admissionId) => apiFetch(`/admissions/${admissionId}/transfers`);
export const getAdmissionEvents = (admissionId) => apiFetch(`/admissions/${admissionId}/events`);

// Historical Bed Occupancy Analytics
export const getHistoricalOccupancy = (params = {}) => {
  const query = new URLSearchParams(params).toString();
  return apiFetch(`/analytics/beds/history?${query}`);
};

export const getDailyOccupancy = (dateStr, wardId, bedType) => {
  const params = new URLSearchParams();
  if (dateStr) params.append("date", dateStr);
  if (wardId) params.append("wardId", wardId);
  if (bedType) params.append("bedType", bedType);
  return apiFetch(`/analytics/beds/occupancy?${params.toString()}`);
};

export const getLengthOfStayAnalytics = (preset = "30D") => apiFetch(`/analytics/beds/length-of-stay?preset=${preset}`);

// Legacy Compatibility Aliases
export const updateBedStatus = (id, status) => apiFetch(`/beds/${id}`, { method: "PUT", body: JSON.stringify({ status }) });
export const assignBed = (data) => admitPatient(data);
export const releaseBed = (bookingId) => dischargePatient(bookingId);
export const getBedBookings = () => apiFetch("/beds/bookings");
