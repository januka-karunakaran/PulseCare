/**
 * Enterprise Data Formatting Utilities
 * Standardized across all Hospital Management System modules
 */

export function formatCurrency(amount, currency = "LKR") {
  if (amount == null || isNaN(amount)) return "0.00";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency,
    minimumFractionDigits: 2,
  }).format(amount);
}

export function formatDate(dateString, options = {}) {
  if (!dateString) return "N/A";
  try {
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return "Invalid Date";
    return new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      ...options,
    }).format(date);
  } catch {
    return String(dateString);
  }
}

export function formatDateTime(dateString) {
  return formatDate(dateString, {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

export function capitalize(str) {
  if (!str) return "";
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

export function truncate(str, maxLength = 30) {
  if (!str) return "";
  return str.length > maxLength ? str.slice(0, maxLength) + "..." : str;
}
