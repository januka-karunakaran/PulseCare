"use client";

import { useEffect, useState, useMemo } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getExecutiveOverview,
  getAppointmentAnalytics,
  getPatientOperations,
  getBedUtilization,
  getLaboratoryAnalytics,
  getPharmacyAnalytics,
  getBillingAnalytics,
  getFinancialReconciliation,
  exportAnalyticsReport
} from "@/services/analyticsService";
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell
} from "recharts";
import {
  FaFilePdf, FaFileCsv, FaCalendarAlt, FaUserMd, FaUsers, FaPrescriptionBottleAlt, FaBed, FaMoneyBillWave, FaFlask, FaExclamationTriangle, FaSync
} from "react-icons/fa";

const COLORS = ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#06b6d4"];

export default function AnalyticsDashboardPage() {
  const [dateRange, setDateRange] = useState("LAST_30_DAYS");
  const [overview, setOverview] = useState(null);
  const [appointmentData, setAppointmentData] = useState(null);
  const [bedData, setBedData] = useState(null);
  const [labData, setLabData] = useState(null);
  const [pharmacyData, setPharmacyData] = useState(null);
  const [billingData, setBillingData] = useState(null);
  const [reconciliation, setReconciliation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [exporting, setExporting] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      setError("");
      const params = { dateRange };
      const [ov, appt, bd, lb, ph, bl, rc] = await Promise.all([
        getExecutiveOverview(params),
        getAppointmentAnalytics(params),
        getBedUtilization(),
        getLaboratoryAnalytics(params),
        getPharmacyAnalytics(params),
        getBillingAnalytics(params),
        getFinancialReconciliation()
      ]);
      setOverview(ov);
      setAppointmentData(appt);
      setBedData(bd);
      setLabData(lb);
      setPharmacyData(ph);
      setBillingData(bl);
      setReconciliation(rc);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || "Failed to load analytics data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [dateRange]);

  const handleExport = async (format, reportType) => {
    try {
      setExporting(true);
      const blob = await exportAnalyticsReport({
        reportType,
        format,
        dateRange
      });
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `pulsecare-${reportType.toLowerCase()}-${Date.now()}.${format.toLowerCase()}`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
    } catch (err) {
      alert("Export failed: " + (err.message || "Network error"));
    } finally {
      setExporting(false);
    }
  };

  const bedChartData = useMemo(() => {
    if (!bedData?.statusDistribution) return [];
    return Object.entries(bedData.statusDistribution).map(([name, value]) => ({ name, value }));
  }, [bedData]);

  const apptStatusData = useMemo(() => {
    if (!appointmentData?.statusDistribution) return [];
    return Object.entries(appointmentData.statusDistribution).map(([name, value]) => ({ name, value }));
  }, [appointmentData]);

  return (
    <ProtectedRoute allowedRoles={["ADMIN"]}>
      <div className="layout">
        <Sidebar />
        <main className="main-content">
          <Topbar title="Hospital Executive Analytics & Operational Reporting" />

          {/* Header Controls */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <label style={{ fontSize: 13, color: "var(--text-secondary)", fontWeight: 600 }}>Period:</label>
              <select
                value={dateRange}
                onChange={(e) => setDateRange(e.target.value)}
                style={{ padding: "8px 14px", borderRadius: 8, background: "var(--bg-card)", color: "var(--text-primary)", border: "1px solid var(--border-color)", fontSize: 13 }}
              >
                <option value="TODAY">Today</option>
                <option value="YESTERDAY">Yesterday</option>
                <option value="LAST_7_DAYS">Last 7 Days</option>
                <option value="LAST_30_DAYS">Last 30 Days</option>
                <option value="THIS_MONTH">This Month</option>
                <option value="LAST_MONTH">Last Month</option>
              </select>
              <button
                onClick={loadData}
                className="btn-secondary"
                style={{ padding: "8px 12px", display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}
                title="Refresh Analytics"
              >
                <FaSync /> Refresh
              </button>
            </div>

            <div style={{ display: "flex", gap: 8 }}>
              <button
                onClick={() => handleExport("CSV", "EXECUTIVE_OVERVIEW")}
                disabled={exporting}
                className="btn-secondary"
                style={{ padding: "8px 12px", display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}
              >
                <FaFileCsv style={{ color: "var(--emerald)" }} /> Export CSV
              </button>
              <button
                onClick={() => handleExport("PDF", "EXECUTIVE_OVERVIEW")}
                disabled={exporting}
                className="btn-primary"
                style={{ padding: "8px 12px", display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}
              >
                <FaFilePdf style={{ color: "#fff" }} /> Export PDF
              </button>
            </div>
          </div>

          {error && <div className="alert alert-error" style={{ marginBottom: 16 }}>{error}</div>}

          {/* KPI Summary Cards */}
          <div className="grid-4" style={{ marginBottom: 20 }}>
            <div className="card" style={{ borderLeft: "4px solid var(--primary)" }}>
              <div style={{ fontSize: 12, color: "var(--text-muted)", textTransform: "uppercase" }}>Registered Patients</div>
              <div style={{ fontSize: 24, fontWeight: 800, marginTop: 4 }}>{overview?.totalRegisteredPatients || 0}</div>
              <div style={{ fontSize: 12, color: "var(--emerald)", marginTop: 2 }}>+{overview?.newlyRegisteredPatients || 0} this period</div>
            </div>

            <div className="card" style={{ borderLeft: "4px solid var(--emerald)" }}>
              <div style={{ fontSize: 12, color: "var(--text-muted)", textTransform: "uppercase" }}>Bed Occupancy Rate</div>
              <div style={{ fontSize: 24, fontWeight: 800, marginTop: 4 }}>{overview?.bedOccupancyRate || 0}%</div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>{overview?.occupiedBeds || 0} of {overview?.totalBeds || 0} beds occupied</div>
            </div>

            <div className="card" style={{ borderLeft: "4px solid var(--amber)" }}>
              <div style={{ fontSize: 12, color: "var(--text-muted)", textTransform: "uppercase" }}>Appointment Completion</div>
              <div style={{ fontSize: 24, fontWeight: 800, marginTop: 4 }}>{overview?.appointmentCompletionRate || 0}%</div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>{overview?.completedAppointmentsPeriod || 0} completed / {overview?.appointmentsPeriod || 0} total</div>
            </div>

            <div className="card" style={{ borderLeft: "4px solid #8b5cf6" }}>
              <div style={{ fontSize: 12, color: "var(--text-muted)", textTransform: "uppercase" }}>Confirmed Collections</div>
              <div style={{ fontSize: 24, fontWeight: 800, marginTop: 4 }}>{overview?.currency || "LKR"} {Number(overview?.confirmedCollections || 0).toLocaleString()}</div>
              <div style={{ fontSize: 12, color: "var(--rose)", marginTop: 2 }}>{overview?.currency || "LKR"} {Number(overview?.outstandingReceivables || 0).toLocaleString()} outstanding</div>
            </div>
          </div>

          {/* Appointments & Operations Trends */}
          <div className="grid-2" style={{ marginBottom: 20 }}>
            <div className="card">
              <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 14 }}>Appointment Daily Trends</div>
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={appointmentData?.dailyTrends || []}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                  <XAxis dataKey="date" tick={{ fill: "#64748b", fontSize: 11 }} />
                  <YAxis tick={{ fill: "#64748b", fontSize: 11 }} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="total" name="Total" stroke="#3b82f6" strokeWidth={2} />
                  <Line type="monotone" dataKey="completed" name="Completed" stroke="#10b981" strokeWidth={2} />
                  <Line type="monotone" dataKey="cancelled" name="Cancelled" stroke="#ef4444" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            <div className="card">
              <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 14 }}>Daily Revenue & Collections</div>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={billingData?.dailyRevenueTrends || []}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                  <XAxis dataKey="date" tick={{ fill: "#64748b", fontSize: 11 }} />
                  <YAxis tick={{ fill: "#64748b", fontSize: 11 }} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="billedAmount" name="Billed" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="collectedAmount" name="Collected" fill="#10b981" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Departmental Operations Breakdown */}
          <div className="grid-3" style={{ marginBottom: 20 }}>
            {/* Laboratory Snapshot */}
            <div className="card">
              <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, fontSize: 15, marginBottom: 12 }}>
                <FaFlask style={{ color: "var(--primary)" }} /> Laboratory Operations
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8, fontSize: 13 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Total Orders:</span>
                  <span style={{ fontWeight: 700 }}>{labData?.totalOrders || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Pending Collection:</span>
                  <span style={{ fontWeight: 700, color: "var(--amber)" }}>{labData?.pendingCollectionOrders || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Results Released:</span>
                  <span style={{ fontWeight: 700, color: "var(--emerald)" }}>{labData?.resultsReleased || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Avg Turnaround:</span>
                  <span style={{ fontWeight: 700 }}>{labData?.averageTurnaroundMinutes || 0} mins</span>
                </div>
              </div>
            </div>

            {/* Pharmacy Snapshot */}
            <div className="card">
              <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, fontSize: 15, marginBottom: 12 }}>
                <FaPrescriptionBottleAlt style={{ color: "var(--emerald)" }} /> Pharmacy Inventory
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8, fontSize: 13 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Catalog Medicines:</span>
                  <span style={{ fontWeight: 700 }}>{pharmacyData?.activeMedicineCatalogCount || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Low Stock Items:</span>
                  <span style={{ fontWeight: 700, color: "var(--rose)" }}>{pharmacyData?.lowStockItemsCount || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Near Expiry Batches:</span>
                  <span style={{ fontWeight: 700, color: "var(--amber)" }}>{pharmacyData?.nearExpiryBatchesCount || 0}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "var(--text-secondary)" }}>Completed Dispenses:</span>
                  <span style={{ fontWeight: 700 }}>{pharmacyData?.totalDispenses || 0}</span>
                </div>
              </div>
            </div>

            {/* Bed Occupancy Snapshot */}
            <div className="card">
              <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, fontSize: 15, marginBottom: 12 }}>
                <FaBed style={{ color: "#8b5cf6" }} /> Bed Distribution
              </div>
              <ResponsiveContainer width="100%" height={140}>
                <PieChart>
                  <Pie data={bedChartData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={50} label>
                    {bedChartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Financial Reconciliation Audit Warning Card */}
          {reconciliation?.totalInconsistenciesDetected > 0 && (
            <div className="card" style={{ borderLeft: "4px solid var(--rose)", marginBottom: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, color: "var(--rose)", fontSize: 16, marginBottom: 8 }}>
                <FaExclamationTriangle /> Financial Reconciliation Discrepancies ({reconciliation.totalInconsistenciesDetected})
              </div>
              <div style={{ fontSize: 13, color: "var(--text-secondary)", marginBottom: 12 }}>
                The automated audit engine detected ledger or balance discrepancies requiring manual accounting review:
              </div>
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", fontSize: 12, borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid var(--border-color)", textAlign: "left", color: "var(--text-muted)" }}>
                      <th style={{ padding: 6 }}>Type</th>
                      <th style={{ padding: 6 }}>Entity</th>
                      <th style={{ padding: 6 }}>Reference</th>
                      <th style={{ padding: 6 }}>Description</th>
                      <th style={{ padding: 6 }}>Severity</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reconciliation.anomalies.slice(0, 5).map((a, idx) => (
                      <tr key={idx} style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                        <td style={{ padding: 6, fontWeight: 600 }}>{a.anomalyType}</td>
                        <td style={{ padding: 6 }}>{a.referenceEntity}</td>
                        <td style={{ padding: 6 }}>{a.referenceNumber || a.entityId}</td>
                        <td style={{ padding: 6 }}>{a.description}</td>
                        <td style={{ padding: 6 }}><span className="badge badge-red">{a.severity}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </main>
      </div>
    </ProtectedRoute>
  );
}
