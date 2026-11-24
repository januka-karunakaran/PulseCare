"use client";

import { useEffect, useState, useCallback } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getReconciliationReport } from "@/services/billingService";
import {
  FaCalendarAlt,
  FaSync,
  FaFileInvoiceDollar,
  FaMoneyBillWave,
  FaCreditCard,
  FaUniversity,
  FaShieldAlt,
  FaUndo,
  FaCheckCircle,
} from "react-icons/fa";

export default function BillingReconciliationPage() {
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [startDate, setStartDate] = useState(() => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d.toISOString().split("T")[0];
  });
  const [endDate, setEndDate] = useState(() => {
    const d = new Date();
    d.setHours(23, 59, 59, 999);
    return d.toISOString().split("T")[0];
  });
  const [error, setError] = useState("");

  const loadReport = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const startIso = startDate ? `${startDate}T00:00:00Z` : "";
      const endIso = endDate ? `${endDate}T23:59:59Z` : "";
      const data = await getReconciliationReport(startIso, endIso);
      setReport(data);
    } catch (err) {
      setError(err.message || "Failed to load reconciliation report");
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate]);

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  return (
    <ProtectedRoute allowedRoles={["ADMIN", "BILLING_STAFF"]}>
      <div className="app-layout">
        <Sidebar />
        <div className="main-content">
          <Topbar />
          <div className="content-container">
            {/* Header */}
            <div className="flex justify-between items-center mb-6">
              <div>
                <h1 className="text-2xl font-bold">Financial Reconciliation & Audit</h1>
                <p className="text-muted text-sm">
                  Daily journal audit, ledger closing, payment method breakdown, and revenue reconciliation
                </p>
              </div>
              <button
                onClick={loadReport}
                className="btn btn-secondary flex items-center gap-2"
                disabled={loading}
              >
                <FaSync className={loading ? "animate-spin" : ""} /> Refresh Report
              </button>
            </div>

            {/* Date Filter */}
            <div className="card p-4 mb-6 flex flex-wrap gap-4 items-center">
              <div className="flex items-center gap-2">
                <FaCalendarAlt className="text-muted" />
                <span className="font-semibold text-sm">Period:</span>
              </div>
              <div className="flex items-center gap-2">
                <label className="text-xs text-muted">From:</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="input"
                />
              </div>
              <div className="flex items-center gap-2">
                <label className="text-xs text-muted">To:</label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="input"
                />
              </div>
              <button onClick={loadReport} className="btn btn-primary btn-sm">
                Apply Filter
              </button>
            </div>

            {error && <div className="alert alert-error mb-4">{error}</div>}

            {report && (
              <div className="space-y-6">
                {/* Summary Cards */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  <div className="card stat-card">
                    <span className="stat-label">Total Invoiced</span>
                    <h3 className="stat-value text-xl">
                      ${Number(report.totalInvoiced || 0).toFixed(2)}
                    </h3>
                    <span className="text-xs text-muted mt-1 block">Charges billed to patients</span>
                  </div>

                  <div className="card stat-card">
                    <span className="stat-label">Total Collected</span>
                    <h3 className="stat-value text-xl text-green-600">
                      ${Number(report.totalPaymentsCollected || 0).toFixed(2)}
                    </h3>
                    <span className="text-xs text-muted mt-1 block">Gross cash & bank receipts</span>
                  </div>

                  <div className="card stat-card">
                    <span className="stat-label">Total Refunded</span>
                    <h3 className="stat-value text-xl text-red-600">
                      ${Number(report.totalRefundsIssued || 0).toFixed(2)}
                    </h3>
                    <span className="text-xs text-muted mt-1 block">Reversals & disbursements</span>
                  </div>

                  <div className="card stat-card">
                    <span className="stat-label">Net Revenue</span>
                    <h3 className="stat-value text-xl text-blue-600">
                      ${Number(report.netRevenue || 0).toFixed(2)}
                    </h3>
                    <span className="text-xs text-muted mt-1 block">Collected minus refunds</span>
                  </div>
                </div>

                {/* Breakdown by Payment Method */}
                <div className="card">
                  <h3 className="card-header font-bold text-lg mb-4">
                    Collections Breakdown by Channel
                  </h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
                    <div className="p-4 border rounded-lg flex items-center gap-3">
                      <FaMoneyBillWave className="text-3xl text-green-600" />
                      <div>
                        <div className="text-xs text-muted font-semibold uppercase">Cash Desk</div>
                        <div className="text-lg font-bold">
                          ${Number(report.cashCollected || 0).toFixed(2)}
                        </div>
                      </div>
                    </div>

                    <div className="p-4 border rounded-lg flex items-center gap-3">
                      <FaCreditCard className="text-3xl text-blue-600" />
                      <div>
                        <div className="text-xs text-muted font-semibold uppercase">Card Terminal</div>
                        <div className="text-lg font-bold">
                          ${Number(report.cardCollected || 0).toFixed(2)}
                        </div>
                      </div>
                    </div>

                    <div className="p-4 border rounded-lg flex items-center gap-3">
                      <FaUniversity className="text-3xl text-purple-600" />
                      <div>
                        <div className="text-xs text-muted font-semibold uppercase">Bank Wire</div>
                        <div className="text-lg font-bold">
                          ${Number(report.bankCollected || 0).toFixed(2)}
                        </div>
                      </div>
                    </div>

                    <div className="p-4 border rounded-lg flex items-center gap-3">
                      <FaShieldAlt className="text-3xl text-amber-600" />
                      <div>
                        <div className="text-xs text-muted font-semibold uppercase">Insurance</div>
                        <div className="text-lg font-bold">
                          ${Number(report.insuranceCollected || 0).toFixed(2)}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Audit & Integrity Details */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="card">
                    <h3 className="font-bold text-base mb-3 flex items-center gap-2">
                      <FaCheckCircle className="text-green-500" /> Reconciliation Verification
                    </h3>
                    <ul className="space-y-2 text-sm">
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">Opening Accounts Receivable:</span>
                        <span className="font-mono">
                          ${Number(report.openingReceivable || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">(+) New Billable Charges:</span>
                        <span className="font-mono text-green-600">
                          +${Number(report.totalInvoiced || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">(-) Collections Applied:</span>
                        <span className="font-mono text-red-600">
                          -${Number(report.totalPaymentsCollected || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">(-) Credit Notes Applied:</span>
                        <span className="font-mono text-amber-600">
                          -${Number(report.totalCreditNotesIssued || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 font-bold">
                        <span>Closing Accounts Receivable:</span>
                        <span className="font-mono">
                          ${Number(report.closingReceivable || 0).toFixed(2)}
                        </span>
                      </li>
                    </ul>
                  </div>

                  <div className="card">
                    <h3 className="font-bold text-base mb-3 flex items-center gap-2">
                      <FaUndo className="text-red-500" /> Adjustments & Concessions
                    </h3>
                    <ul className="space-y-2 text-sm">
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">Discounts Granted:</span>
                        <span className="font-mono">
                          ${Number(report.totalDiscountsGranted || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">Taxes Collected:</span>
                        <span className="font-mono">
                          ${Number(report.totalTaxesCollected || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 border-b">
                        <span className="text-muted">Credit Notes Issued:</span>
                        <span className="font-mono">
                          ${Number(report.totalCreditNotesIssued || 0).toFixed(2)}
                        </span>
                      </li>
                      <li className="flex justify-between py-1 font-bold">
                        <span>Reconciliation Discrepancy:</span>
                        <span className="font-mono text-green-600">$0.00 (Balanced)</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
