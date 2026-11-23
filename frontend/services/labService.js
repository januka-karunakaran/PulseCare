"use client";

import { apiFetch } from "./api";
import { API_BASE } from "@/utils/constants";
import { getAccessToken } from "@/utils/auth";

// Catalog
export const getActiveLabTests = () => apiFetch("/lab/tests");
export const searchLabTests = (query = "", page = 0, size = 20) =>
  apiFetch(`/lab/tests/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
export const getLabTestById = (id) => apiFetch(`/lab/tests/${id}`);
export const createLabTest = (data) =>
  apiFetch("/lab/tests", {
    method: "POST",
    body: JSON.stringify(data),
  });
export const updateLabTest = (id, data) =>
  apiFetch(`/lab/tests/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
export const deactivateLabTest = (id) =>
  apiFetch(`/lab/tests/${id}`, {
    method: "DELETE",
  });

// Orders
export const createLabOrder = (data) =>
  apiFetch("/lab/orders", {
    method: "POST",
    body: JSON.stringify(data),
  });
export const getLabOrders = (status = "", page = 0, size = 20) => {
  const query = status ? `?status=${status}&page=${page}&size=${size}` : `?page=${page}&size=${size}`;
  return apiFetch(`/lab/orders${query}`);
};
export const getLabOrderDetails = (id) => apiFetch(`/lab/orders/${id}`);
export const cancelLabOrder = (id, reason) =>
  apiFetch(`/lab/orders/${id}/cancel`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
export const getPatientLabOrders = (patientId) => apiFetch(`/lab/patients/${patientId}/orders`);

// Specimens
export const collectSpecimen = (orderId, data) =>
  apiFetch(`/lab/orders/${orderId}/specimens`, {
    method: "POST",
    body: JSON.stringify(data),
  });
export const getOrderSpecimens = (orderId) => apiFetch(`/lab/orders/${orderId}/specimens`);
export const receiveSpecimen = (id) =>
  apiFetch(`/lab/specimens/${id}/receive`, {
    method: "PUT",
  });
export const rejectSpecimen = (id, rejectionReason) =>
  apiFetch(`/lab/specimens/${id}/reject`, {
    method: "PUT",
    body: JSON.stringify({ rejectionReason }),
  });

// Results & Verification
export const enterLabResult = (orderId, data) =>
  apiFetch(`/lab/orders/${orderId}/results`, {
    method: "POST",
    body: JSON.stringify(data),
  });
export const getOrderResults = (orderId) => apiFetch(`/lab/orders/${orderId}/results`);
export const saveDraftLabResult = (id, data) =>
  apiFetch(`/lab/results/${id}/draft`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
export const verifyLabResult = (id, notes = "") =>
  apiFetch(`/lab/results/${id}/verify`, {
    method: "POST",
    body: JSON.stringify({ notes }),
  });
export const releaseLabResult = (id) =>
  apiFetch(`/lab/results/${id}/release`, {
    method: "POST",
  });
export const correctLabResult = (id, data) =>
  apiFetch(`/lab/results/${id}/correct`, {
    method: "POST",
    body: JSON.stringify(data),
  });
export const getLabResultById = (id) => apiFetch(`/lab/results/${id}`);
export const getPatientReleasedReports = (patientId) => apiFetch(`/lab/patients/${patientId}/reports`);

// PDF Download
export const downloadLabReportPdf = async (resultId) => {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE}/lab/results/${resultId}/pdf`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!res.ok) {
    throw new Error("Failed to download PDF lab report");
  }
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `lab-report-${resultId}.pdf`;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
};
