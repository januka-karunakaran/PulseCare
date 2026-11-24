"use client";

import { useEffect, useState, useCallback } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getMyInvoices,
  getMyPayments,
  downloadInvoicePdf,
  downloadReceiptPdf,
} from "@/services/billingService";
import {
  FaMoneyBillWave,
  FaFilePdf,
  FaReceipt,
  FaSync,
  FaExclamationCircle,
} from "react-icons/fa";

export default function PatientBillingPage() {
  const [invoices, setInvoices] = useState([]);
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState("invoices");

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const [invData, payData] = await Promise.all([
        getMyInvoices(),
        getMyPayments(),
      ]);
      setInvoices(invData?.content || []);
      setPayments(payData?.content || []);
    } catch (err) {
      setError(err.message || "Failed to load billing history");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleDownloadInvoice = async (inv) => {
    try {
      await downloadInvoicePdf(inv.id, inv.invoiceNumber);
    } catch (err) {
      alert("Error downloading invoice: " + err.message);
    }
  };

  const handleDownloadReceipt = async (pay) => {
    try {
      await downloadReceiptPdf(pay.id, pay.paymentNumber);
    } catch (err) {
      alert("Error downloading receipt: " + err.message);
    }
  };

  const totalOutstanding = invoices.reduce(
    (sum, inv) => sum + Number(inv.outstandingAmount || 0),
    0
  );

  const getStatusBadge = (status) => {
    switch (status) {
      case "PAID":
        return <span className="badge badge-green">PAID</span>;
      case "PARTIALLY_PAID":
        return <span className="badge badge-yellow">PARTIALLY PAID</span>;
      case "ISSUED":
        return <span className="badge badge-blue">DUE</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

  return (
    <ProtectedRoute allowedRoles={["PATIENT"]}>
      <div className="app-layout">
        <Sidebar />
        <div className="main-content">
          <Topbar />
          <div className="content-container">
            {/* Header */}
            <div className="flex justify-between items-center mb-6">
              <div>
                <h1 className="text-2xl font-bold">My Medical Bills & Payments</h1>
                <p className="text-muted text-sm">
                  View hospital invoices, payment receipts, and download official statement PDFs
                </p>
              </div>
              <button
                onClick={loadData}
                className="btn btn-secondary flex items-center gap-2"
                disabled={loading}
              >
                <FaSync className={loading ? "animate-spin" : ""} /> Refresh
              </button>
            </div>

            {error && <div className="alert alert-error mb-4">{error}</div>}

            {/* Outstanding Balance Banner */}
            <div className="card p-6 mb-6 flex flex-wrap justify-between items-center bg-gradient-to-r from-blue-900 to-indigo-900 text-white rounded-xl">
              <div>
                <span className="text-blue-200 text-sm font-semibold uppercase tracking-wider">
                  Current Total Outstanding Balance
                </span>
                <h2 className="text-3xl font-extrabold mt-1">
                  ${totalOutstanding.toFixed(2)}
                </h2>
                <p className="text-blue-200 text-xs mt-1">
                  Payable at hospital cashier or front billing desk
                </p>
              </div>
              <div className="text-4xl text-blue-300">
                <FaMoneyBillWave />
              </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-4 border-b mb-6">
              <button
                onClick={() => setActiveTab("invoices")}
                className={`pb-3 font-semibold text-sm border-b-2 flex items-center gap-2 ${
                  activeTab === "invoices"
                    ? "border-primary text-primary"
                    : "border-transparent text-muted hover:text-foreground"
                }`}
              >
                <FaReceipt /> Invoices ({invoices.length})
              </button>
              <button
                onClick={() => setActiveTab("payments")}
                className={`pb-3 font-semibold text-sm border-b-2 flex items-center gap-2 ${
                  activeTab === "payments"
                    ? "border-primary text-primary"
                    : "border-transparent text-muted hover:text-foreground"
                }`}
              >
                <FaMoneyBillWave /> Payment Receipts ({payments.length})
              </button>
            </div>

            {/* Invoices Tab */}
            {activeTab === "invoices" && (
              <div className="card">
                <div className="overflow-x-auto">
                  <table className="table w-full">
                    <thead>
                      <tr>
                        <th>Invoice #</th>
                        <th>Issued Date</th>
                        <th>Due Date</th>
                        <th>Total</th>
                        <th>Paid</th>
                        <th>Outstanding</th>
                        <th>Status</th>
                        <th>Statement</th>
                      </tr>
                    </thead>
                    <tbody>
                      {invoices.length === 0 ? (
                        <tr>
                          <td colSpan={8} className="text-center p-8 text-muted">
                            {loading ? "Loading invoices..." : "No invoices found on record."}
                          </td>
                        </tr>
                      ) : (
                        invoices.map((inv) => (
                          <tr key={inv.id}>
                            <td className="font-mono font-bold text-primary">
                              {inv.invoiceNumber}
                            </td>
                            <td className="text-sm">
                              {inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : "-"}
                            </td>
                            <td className="text-sm">
                              {inv.dueAt ? new Date(inv.dueAt).toLocaleDateString() : "-"}
                            </td>
                            <td className="font-semibold">
                              ${Number(inv.totalAmount || 0).toFixed(2)}
                            </td>
                            <td className="text-green-600">
                              ${Number(inv.paidAmount || 0).toFixed(2)}
                            </td>
                            <td className="text-amber-600 font-bold">
                              ${Number(inv.outstandingAmount || 0).toFixed(2)}
                            </td>
                            <td>{getStatusBadge(inv.status)}</td>
                            <td>
                              <button
                                onClick={() => handleDownloadInvoice(inv)}
                                className="btn btn-sm btn-ghost text-red-500 flex items-center gap-1"
                                title="Download PDF"
                              >
                                <FaFilePdf /> PDF
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Payments Tab */}
            {activeTab === "payments" && (
              <div className="card">
                <div className="overflow-x-auto">
                  <table className="table w-full">
                    <thead>
                      <tr>
                        <th>Receipt #</th>
                        <th>Date & Time</th>
                        <th>Payment Method</th>
                        <th>Amount Paid</th>
                        <th>Reference</th>
                        <th>Status</th>
                        <th>Receipt</th>
                      </tr>
                    </thead>
                    <tbody>
                      {payments.length === 0 ? (
                        <tr>
                          <td colSpan={7} className="text-center p-8 text-muted">
                            {loading ? "Loading payments..." : "No payment receipts recorded."}
                          </td>
                        </tr>
                      ) : (
                        payments.map((p) => (
                          <tr key={p.id}>
                            <td className="font-mono font-bold">{p.paymentNumber}</td>
                            <td className="text-sm">
                              {p.paymentDate ? new Date(p.paymentDate).toLocaleString() : "-"}
                            </td>
                            <td>
                              <span className="badge badge-gray">{p.paymentMethod}</span>
                            </td>
                            <td className="font-bold text-green-600">
                              ${Number(p.amount || 0).toFixed(2)}
                            </td>
                            <td className="text-sm text-muted">{p.transactionReference || "-"}</td>
                            <td>
                              <span className="badge badge-green">{p.status}</span>
                            </td>
                            <td>
                              <button
                                onClick={() => handleDownloadReceipt(p)}
                                className="btn btn-sm btn-ghost text-red-500 flex items-center gap-1"
                                title="Download Official Receipt"
                              >
                                <FaFilePdf /> Receipt
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
