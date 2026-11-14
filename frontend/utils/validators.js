/**
 * Enterprise Form Validation Utilities
 */

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const PHONE_REGEX = /^[+0-9\s-]{7,15}$/;

export function isValidEmail(email) {
  return typeof email === "string" && EMAIL_REGEX.test(email.trim());
}

export function isValidPhone(phone) {
  return typeof phone === "string" && PHONE_REGEX.test(phone.trim());
}

export function isValidPassword(password) {
  // At least 6 characters
  return typeof password === "string" && password.length >= 6;
}

export function isValidRequired(value) {
  if (value == null) return false;
  if (typeof value === "string") return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  return true;
}
