"use client";

import { useEffect, useState, useCallback } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getBillingSummary,
  getInvoices,
  downloadInvoicePdf,
} from "@/services/billingService";
import Link from "next/link";
import {
  FaMoneyBillWave,
  FaFileInvoiceDollar,
  FaReceipt,
  FaExclamationTriangle,
  FaFilePdf,
  FaSync,
  FaPlus,
  FaCalendarAlt,
} from "react-icons/fa";

export default function BillingDashboardPage() {
  const [summary, setSummary] = useState(null);
  const [recentInvoices, setRecentInvoices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const [sumData, invPage] = await Promise.all([
        getBillingSummary(),
        getInvoices("", 0, 10),
      ]);
      setSummary(sumData);
      setRecentInvoices(invPage?.content || []);
    } catch (err) {
      setError(err.message || "Failed to load billing metrics");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleDownloadPdf = async (inv) => {
    try {
      await downloadInvoicePdf(inv.id, inv.invoiceNumber);
    } catch (err) {
      alert("Error downloading invoice PDF: " + err.message);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "PAID":
        return <span className="badge badge-green">PAID</span>;
      case "PARTIALLY_PAID":
        return <span className="badge badge-yellow">PARTIALLY PAID</span>;
      case "ISSUED":
        return <span className="badge badge-blue">ISSUED</span>;
      case "DRAFT":
        return <span className="badge badge-gray">DRAFT</span>;
      case "VOID":
        return <span className="badge badge-red">VOID</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

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
                <h1 className="text-2xl font-bold">Billing & Invoicing Dashboard</h1>
                <p className="text-muted text-sm">
                  Hospital revenue integrity, accounts receivable, and cash collections
                </p>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={loadData}
                  className="btn btn-secondary flex items-center gap-2"
                  disabled={loading}
                >
                  <FaSync className={loading ? "animate-spin" : ""} /> Refresh
                </button>
                <Link href="/admin/billing" className="btn btn-primary flex items-center gap-2">
                  <FaPlus /> Manage Invoices
                </Link>
              </div>
            </div>

            {error && (
              <div className="alert alert-error mb-4">
                <FaExclamationTriangle /> {error}
              </div>
            )}

            {/* Metrics Grid */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
              <div className="card stat-card">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="stat-label">Total Invoiced</span>
                    <h3 className="stat-value text-xl">
                      ${summary ? Number(summary.totalInvoiced || 0).toFixed(2) : "0.00"}
                    </h3>
                  </div>
                  <div className="stat-icon bg-blue-100 text-blue-600">
                    <FaFileInvoiceDollar />
                  </div>
                </div>
                <span className="text-xs text-muted mt-2 block">
                  {summary ? summary.totalInvoices : 0} total invoices
                </span>
              </div>

              <div className="card stat-card">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="stat-label">Total Collected</span>
                    <h3 className="stat-value text-xl text-green-600">
                      ${summary ? Number(summary.totalCollected || 0).toFixed(2) : "0.00"}
                    </h3>
                  </div>
                  <div className="stat-icon bg-green-100 text-green-600">
                    <FaMoneyBillWave />
                  </div>
                </div>
                <span className="text-xs text-muted mt-2 block">
                  {summary ? summary.paidCount : 0} fully paid invoices
                </span>
              </div>

              <div className="card stat-card">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="stat-label">Outstanding Balance</span>
                    <h3 className="stat-value text-xl text-amber-600">
                      ${summary ? Number(summary.totalOutstanding || 0).toFixed(2) : "0.00"}
                    </h3>
                  </div>
                  <div className="stat-icon bg-yellow-100 text-yellow-600">
                    <FaReceipt />
                  </div>
                </div>
                <span className="text-xs text-muted mt-2 block">
                  {summary ? (summary.issuedCount || 0) + (summary.partiallyPaidCount || 0) : 0}{" "}
                  pending collection
                </span>
              </div>

              <div className="card stat-card">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="stat-label">Total Refunded</span>
                    <h3 className="stat-value text-xl text-red-600">
                      ${summary ? Number(summary.totalRefunded || 0).toFixed(2) : "0.00"}
                    </h3>
                  </div>
                  <div className="stat-icon bg-red-100 text-red-600">
                    <FaExclamationTriangle />
                  </div>
                </div>
                <span className="text-xs text-muted mt-2 block">Approved reversals & returns</span>
              </div>
            </div>

            {/* Quick Links */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
              <Link href="/admin/billing" className="card p-4 hover:shadow-md transition">
                <div className="flex items-center gap-3">
                  <FaMoneyBillWave className="text-2xl text-blue-500" />
                  <div>
                    <h4 className="font-semibold">Invoice Management</h4>
                    <p className="text-xs text-muted">
                      Draft, issue, void invoices and collect patient payments
                    </p>
                  </div>
                </div>
              </Link>
              <Link href="/billing/services" className="card p-4 hover:shadow-md transition">
                <div className="flex items-center gap-3">
                  <FaFileInvoiceDollar className="text-2xl text-green-500" />
                  <div>
                    <h4 className="font-semibold">Service Price Catalog</h4>
                    <p className="text-xs text-muted">
                      Configure service codes, standard fees, and tax rates
                    </p>
                  </div>
                </div>
              </Link>
              <Link href="/billing/reconciliation" className="card p-4 hover:shadow-md transition">
                <div className="flex items-center gap-3">
                  <FaCalendarAlt className="text-2xl text-purple-500" />
                  <div>
                    <h4 className="font-semibold">Daily Reconciliation</h4>
                    <p className="text-xs text-muted">
                      End-of-day cash reconciliation & audit reports
                    </p>
                  </div>
                </div>
              </Link>
            </div>

            {/* Recent Invoices Table */}
            <div className="card">
              <div className="card-header flex justify-between items-center mb-4">
                <h3 className="font-semibold">Recent Invoices</h3>
                <Link href="/admin/billing" className="text-sm text-primary hover:underline">
                  View All &rarr;
                </Link>
              </div>

              {recentInvoices.length === 0 ? (
                <div className="p-8 text-center text-muted">
                  No billing transactions recorded yet.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="table w-full">
                    <thead>
                      <tr>
                        <th>Invoice #</th>
                        <th>Patient</th>
                        <th>Date</th>
                        <th>Total</th>
                        <th>Paid</th>
                        <th>Balance</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentInvoices.map((inv) => (
                        <tr key={inv.id}>
                          <td className="font-mono font-semibold">{inv.invoiceNumber}</td>
                          <td>{inv.patientName || inv.patientId}</td>
                          <td className="text-sm text-muted">
                            {inv.issuedAt
                              ? new Date(inv.issuedAt).toLocaleDateString()
                              : inv.createdAt
                              ? new Date(inv.createdAt).toLocaleDateString()
                              : "-"}
                          </td>
                          <td className="font-semibold">${Number(inv.totalAmount || 0).toFixed(2)}</td>
                          <td className="text-green-600">
                            ${Number(inv.paidAmount || 0).toFixed(2)}
                          </td>
                          <td className="text-amber-600 font-semibold">
                            ${Number(inv.outstandingAmount || 0).toFixed(2)}
                          </td>
                          <td>{getStatusBadge(inv.status)}</td>
                          <td>
                            <button
                              onClick={() => handleDownloadPdf(inv)}
                              className="btn btn-sm btn-ghost flex items-center gap-1 text-red-500"
                              title="Download PDF"
                            >
                              <FaFilePdf /> PDF
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
