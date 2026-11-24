"use client";

import { apiFetch } from "./api";
import { API_BASE } from "@/utils/constants";
import { getAccessToken } from "@/utils/auth";

// Catalog
export const getCatalogServices = (category = "") => {
  const q = category ? `?category=${encodeURIComponent(category)}` : "";
  return apiFetch(`/billing/services${q}`);
};

export const createCatalogService = (data) =>
  apiFetch("/billing/services", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const updateCatalogService = (id, data) =>
  apiFetch(`/billing/services/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });

// Charges
export const getPendingCharges = (patientId = "") => {
  const q = patientId ? `?patientId=${encodeURIComponent(patientId)}` : "";
  return apiFetch(`/billing/charges${q}`);
};

export const createManualCharge = (data) =>
  apiFetch("/billing/charges", {
    method: "POST",
    body: JSON.stringify(data),
  });

// Invoices
export const getInvoices = (status = "", page = 0, size = 50) => {
  let q = `?page=${page}&size=${size}`;
  if (status) q += `&status=${encodeURIComponent(status)}`;
  return apiFetch(`/billing/invoices${q}`);
};

export const getInvoiceById = (id) => apiFetch(`/billing/invoices/${id}`);

export const createInvoiceDraft = (data) =>
  apiFetch("/billing/invoices/draft", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const issueInvoice = (id) =>
  apiFetch(`/billing/invoices/${id}/issue`, {
    method: "POST",
  });

export const voidInvoice = (id, reason) =>
  apiFetch(`/billing/invoices/${id}/void`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });

// Payments
export const recordPayment = (invoiceId, data) =>
  apiFetch(`/billing/invoices/${invoiceId}/payments`, {
    method: "POST",
    body: JSON.stringify(data),
  });

export const processRefund = (paymentId, data) =>
  apiFetch(`/billing/payments/${paymentId}/refund`, {
    method: "POST",
    body: JSON.stringify(data),
  });

export const issueCreditNote = (data) =>
  apiFetch("/billing/credit-notes", {
    method: "POST",
    body: JSON.stringify(data),
  });

// Patient Portal
export const getMyInvoices = (page = 0, size = 20) =>
  apiFetch(`/billing/my-invoices?page=${page}&size=${size}`);

export const getMyPayments = (page = 0, size = 20) =>
  apiFetch(`/billing/my-payments?page=${page}&size=${size}`);

// Reports
export const getBillingSummary = () => apiFetch("/billing/reports/summary");

export const getReconciliationReport = (startDate = "", endDate = "") => {
  let q = "";
  if (startDate && endDate) {
    q = `?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`;
  }
  return apiFetch(`/billing/reconciliation${q}`);
};

// PDF Downloads
export const downloadPdfBlob = async (endpoint, filename) => {
  const token = getAccessToken();
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to download PDF (${response.status})`);
  }
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
};

export const downloadInvoicePdf = (invoiceId, invoiceNumber = "invoice") =>
  downloadPdfBlob(`/billing/invoices/${invoiceId}/pdf`, `${invoiceNumber}.pdf`);

export const downloadReceiptPdf = (paymentId, paymentNumber = "receipt") =>
  downloadPdfBlob(`/billing/payments/${paymentId}/receipt/pdf`, `${paymentNumber}.pdf`);
