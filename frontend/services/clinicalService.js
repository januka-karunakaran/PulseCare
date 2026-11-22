"use client";

import { apiFetch } from "./api";

export const startOrResumeEncounter = (data) =>
  apiFetch("/clinical/encounters", {
    method: "POST",
    body: JSON.stringify(data),
  });

export const getEncounterById = (id) =>
  apiFetch(`/clinical/encounters/${id}`);

export const getPatientEncounters = (patientId) =>
  apiFetch(`/clinical/patients/${patientId}/encounters`);

export const updateEncounterDraft = (id, data) =>
  apiFetch(`/clinical/encounters/${id}/draft`, {
    method: "PUT",
    body: JSON.stringify(data),
  });

export const saveSoapNoteDraft = (encounterId, data) =>
  apiFetch(`/clinical/encounters/${encounterId}/soap`, {
    method: "PUT",
    body: JSON.stringify(data),
  });

export const signEncounter = (encounterId, data) =>
  apiFetch(`/clinical/encounters/${encounterId}/sign`, {
    method: "POST",
    body: JSON.stringify(data),
  });

export const amendEncounter = (encounterId, data) =>
  apiFetch(`/clinical/encounters/${encounterId}/amend`, {
    method: "POST",
    body: JSON.stringify(data),
  });

export const recordVitals = (encounterId, data) =>
  apiFetch(`/clinical/encounters/${encounterId}/vitals`, {
    method: "POST",
    body: JSON.stringify(data),
  });

export const getEncounterVitals = (encounterId) =>
  apiFetch(`/clinical/encounters/${encounterId}/vitals`);

export const getPatientVitals = (patientId) =>
  apiFetch(`/clinical/patients/${patientId}/vitals`);

export const getPatientProfile = (patientId) =>
  apiFetch(`/clinical/patients/${patientId}/profile`);

export const updatePatientProfile = (patientId, data) =>
  apiFetch(`/clinical/patients/${patientId}/profile`, {
    method: "PUT",
    body: JSON.stringify(data),
  });

export const getClinicalTimeline = (patientId) =>
  apiFetch(`/clinical/patients/${patientId}/timeline`);
