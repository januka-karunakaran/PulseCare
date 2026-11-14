/**
 * Enterprise Application Configuration Constants
 * Hospital Management System (HMS)
 */

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080/api";
export const WS_BASE = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080";

// Session & Inactivity Warning
export const WARNING_BEFORE_LOGOUT_MS = 5 * 60 * 1000; // 5 minutes before auto logout
export const SESSION_TIMEOUT_MS = 60 * 60 * 1000; // 1 hour default session

// Storage Keys
export const ACCESS_TOKEN_KEY = "hms_access_token";
export const REFRESH_TOKEN_KEY = "hms_refresh_token";
export const ROLE_KEY = "hms_role";
export const USER_KEY = "hms_user";

// User Roles
export const ROLES = {
  ADMIN: "ADMIN",
  DOCTOR: "DOCTOR",
  PATIENT: "PATIENT",
};

// Appointment Statuses
export const APPOINTMENT_STATUS = {
  SCHEDULED: "SCHEDULED",
  CONFIRMED: "CONFIRMED",
  CANCELLED: "CANCELLED",
  COMPLETED: "COMPLETED",
};

// Bed Statuses
export const BED_STATUS = {
  AVAILABLE: "AVAILABLE",
  OCCUPIED: "OCCUPIED",
  MAINTENANCE: "MAINTENANCE",
  RESERVED: "RESERVED",
};
