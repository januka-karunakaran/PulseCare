"use client";

import { apiFetch } from "./api";

// 1. Medicine Catalog
export const getMedicines = (search = "", page = 0, size = 20) => {
  const q = search ? `&search=${encodeURIComponent(search)}` : "";
  return apiFetch(`/pharmacy/medicines?page=${page}&size=${size}${q}`);
};

export const getMedicineById = (id) => apiFetch(`/pharmacy/medicines/${id}`);

export const createMedicine = (data) =>
  apiFetch("/pharmacy/medicines", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const updateMedicine = (id, data) =>
  apiFetch(`/pharmacy/medicines/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });

export const deactivateMedicine = (id) =>
  apiFetch(`/pharmacy/medicines/${id}`, {
    method: "DELETE",
  });

export const getLowStockMedicines = () => apiFetch("/pharmacy/medicines/low-stock");

// 2. Inventory & Batches
export const receiveStock = (data) =>
  apiFetch("/pharmacy/inventory/receive", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const adjustStock = (data) =>
  apiFetch("/pharmacy/inventory/adjust", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const getBatchesByMedicine = (id) => apiFetch(`/pharmacy/medicines/${id}/batches`);

export const getNearExpiryBatches = (days = 30) => apiFetch(`/pharmacy/batches/near-expiry?days=${days}`);

export const getStockMovements = (medicineId, page = 0, size = 20) =>
  apiFetch(`/pharmacy/medicines/${medicineId}/movements?page=${page}&size=${size}`);

// 3. Prescription Queue
export const getPrescriptionQueue = (page = 0, size = 20) =>
  apiFetch(`/pharmacy/queue?page=${page}&size=${size}`);

export const getPrescriptionQueueItem = (prescriptionId) =>
  apiFetch(`/pharmacy/queue/${prescriptionId}`);

// 4. Dispensing & Reversals
export const dispenseMedication = (data) =>
  apiFetch("/pharmacy/dispense", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const reverseDispense = (data) =>
  apiFetch("/pharmacy/reverse", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const getDispenseById = (id) => apiFetch(`/pharmacy/dispense/${id}`);

export const getPatientDispenses = (patientId, page = 0, size = 20) =>
  apiFetch(`/pharmacy/patients/${patientId}/dispenses?page=${page}&size=${size}`);
