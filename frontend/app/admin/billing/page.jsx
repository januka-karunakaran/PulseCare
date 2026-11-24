"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getInvoices,
  createInvoiceDraft,
  issueInvoice,
  voidInvoice,
  recordPayment,
  downloadInvoicePdf,
} from "@/services/billingService";
import {
  getInvoices as getLegacyInvoices,
  getPatients,
  getAppointments,
} from "@/services/adminService";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts";
import {
  FaMoneyBillWave,
  FaPlus,
  FaSync,
  FaCheckCircle,
  FaClock,
  FaFilePdf,
  FaBan,
  FaCreditCard,
} from "react-icons/fa";

const PIE_COLORS = {
  PAID: "#10b981",
  PARTIALLY_PAID: "#3b82f6",
  ISSUED: "#f59e0b",
  PENDING: "#f59e0b",
  DRAFT: "#94a3b8",
  VOID: "#f43f5e",
  CANCELLED: "#f43f5e",
};

const CustomTooltipStyle = {
  backgroundColor: "#1a2236",
  border: "1px solid rgba(255,255,255,0.12)",
  borderRadius: 10,
  color: "#f1f5f9",
  fontSize: 13,
  padding: "10px 14px",
};

export default function BillingPage() {
  const [invoices, setInvoices] = useState([]);
  const [patients, setPatients] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Payment Modal State
  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [activeInvoice, setActiveInvoice] = useState(null);
  const [paymentForm, setPaymentForm] = useState({
    amount: "",
    paymentMethod: "CASH",
    transactionReference: "",
    notes: "",
  });

  const [form, setForm] = useState({
    patientId: "",
    appointmentId: "",
    consultationFee: "",
    medicineFee: "",
    labFee: "",
    otherFee: "",
    paymentStatus: "PENDING",
    paymentMethod: "CASH",
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const [invPage, patData, apptData] = await Promise.all([
        getInvoices("", 0, 100).catch(() => getLegacyInvoices()),
        getPatients().catch(() => []),
        getAppointments().catch(() => []),
      ]);
      const list = invPage?.content || (Array.isArray(invPage) ? invPage : []);
      setInvoices(list);
      setPatients(patData || []);
      setAppointments(apptData || []);
    } catch (err) {
      setError(err.message || "Failed to load billing data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timerId = window.setTimeout(load, 0);
    return () => window.clearTimeout(timerId);
  }, [load]);

  const totalAmount = useMemo(() => {
    return (
      Number(form.consultationFee || 0) +
      Number(form.medicineFee || 0) +
      Number(form.labFee || 0) +
      Number(form.otherFee || 0)
    );
  }, [form]);

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.patientId) {
      setError("Please select a patient");
      return;
    }
    if (totalAmount <= 0) {
      setError("Total amount must be greater than 0");
      return;
    }

    try {
      setSubmitting(true);
      setError("");
      // Build line items for modern invoice draft
      const items = [];
      if (Number(form.consultationFee) > 0) {
        items.push({
          serviceCode: "CNS-001",
          description: "Consultation Fee",
          quantity: 1,
          unitPrice: Number(form.consultationFee),
          discountAmount: 0,
          taxAmount: 0,
        });
      }
      if (Number(form.medicineFee) > 0) {
        items.push({
          serviceCode: "PHM-001",
          description: "Pharmacy Medication",
          quantity: 1,
          unitPrice: Number(form.medicineFee),
          discountAmount: 0,
          taxAmount: 0,
        });
      }
      if (Number(form.labFee) > 0) {
        items.push({
          serviceCode: "LAB-001",
          description: "Laboratory Diagnostics",
          quantity: 1,
          unitPrice: Number(form.labFee),
          discountAmount: 0,
          taxAmount: 0,
        });
      }
      if (Number(form.otherFee) > 0) {
        items.push({
          serviceCode: "OTH-001",
          description: "Hospital Miscellaneous Service",
          quantity: 1,
          unitPrice: Number(form.otherFee),
          discountAmount: 0,
          taxAmount: 0,
        });
      }

      const draftPayload = {
        patientId: form.patientId,
        appointmentId: form.appointmentId || null,
        items,
        notes: "Hospital Outpatient Services",
      };

      const createdDraft = await createInvoiceDraft(draftPayload);
      if (createdDraft && createdDraft.id) {
        // Automatically issue the created invoice
        await issueInvoice(createdDraft.id);
      }

      setSuccess("Invoice created and issued successfully!");
      setShowForm(false);
      setForm({
        patientId: "",
        appointmentId: "",
        consultationFee: "",
        medicineFee: "",
        labFee: "",
        otherFee: "",
        paymentStatus: "PENDING",
        paymentMethod: "CASH",
      });
      load();
      setTimeout(() => setSuccess(""), 4000);
    } catch (err) {
      setError(err.message || "Failed to create invoice");
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpenPaymentModal = (inv) => {
    setActiveInvoice(inv);
    setPaymentForm({
      amount: inv.outstandingAmount != null ? String(inv.outstandingAmount) : String(inv.totalAmount || ""),
      paymentMethod: "CASH",
      transactionReference: "",
      notes: "Cashier payment counter",
    });
    setPaymentModalOpen(true);
  };

  const handleRecordPayment = async (e) => {
    e.preventDefault();
    if (!activeInvoice) return;
    try {
      setSubmitting(true);
      setError("");
      await recordPayment(activeInvoice.id, {
        amount: parseFloat(paymentForm.amount),
        paymentMethod: paymentForm.paymentMethod,
        transactionReference: paymentForm.transactionReference,
        notes: paymentForm.notes,
      });
      setSuccess(`Payment of $${paymentForm.amount} recorded successfully!`);
      setPaymentModalOpen(false);
      load();
      setTimeout(() => setSuccess(""), 4000);
    } catch (err) {
      setError(err.message || "Payment processing failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleVoidInvoice = async (inv) => {
    const reason = window.prompt("Please state the reason for voiding this invoice:", "Incorrect billing details");
    if (!reason) return;
    try {
      await voidInvoice(inv.id, reason);
      setSuccess(`Invoice ${inv.invoiceNumber || inv.id} voided.`);
      load();
      setTimeout(() => setSuccess(""), 4000);
    } catch (err) {
      setError(err.message || "Failed to void invoice");
    }
  };

  const handleDownloadPdf = async (inv) => {
    try {
      await downloadInvoicePdf(inv.id, inv.invoiceNumber || "invoice");
    } catch (err) {
      alert("Error downloading PDF: " + err.message);
    }
  };

  // Revenue stats
  const revenueStats = useMemo(() => {
    const paid = invoices
      .filter((i) => i.status === "PAID" || i.paymentStatus === "PAID")
      .reduce((s, i) => s + (Number(i.paidAmount) || Number(i.totalAmount) || 0), 0);
    const pending = invoices
      .filter((i) => i.status === "ISSUED" || i.status === "PARTIALLY_PAID" || i.paymentStatus === "PENDING")
      .reduce((s, i) => s + (Number(i.outstandingAmount) || Number(i.totalAmount) || 0), 0);
    const paidCount = invoices.filter((i) => i.status === "PAID" || i.paymentStatus === "PAID").length;
    const pendingCount = invoices.filter(
      (i) => i.status === "ISSUED" || i.status === "PARTIALLY_PAID" || i.paymentStatus === "PENDING"
    ).length;
    const total = invoices.reduce((s, i) => s + (Number(i.totalAmount) || 0), 0);
    return { paid, pending, total, paidCount, pendingCount };
  }, [invoices]);

  const pieData = [
    { name: "Paid", value: revenueStats.paid, color: PIE_COLORS.PAID },
    { name: "Pending", value: revenueStats.pending, color: PIE_COLORS.PENDING },
  ].filter((d) => d.value > 0);

  const getPatientName = (id) =>
    patients.find((p) => (p.userId || p.id) === id)?.fullName || id?.slice(0, 8) + "..." || "—";

  const getStatusBadge = (inv) => {
    const status = inv.status || inv.paymentStatus;
    if (status === "PAID") return <span className="badge badge-green">PAID</span>;
    if (status === "PARTIALLY_PAID") return <span className="badge badge-yellow">PARTIAL</span>;
    if (status === "ISSUED" || status === "PENDING") return <span className="badge badge-blue">ISSUED</span>;
    if (status === "DRAFT") return <span className="badge badge-gray">DRAFT</span>;
    if (status === "VOID" || status === "CANCELLED") return <span className="badge badge-red">VOID</span>;
    return <span className="badge badge-gray">{status || "—"}</span>;
  };

  return (
    <ProtectedRoute allowedRoles={["ADMIN", "BILLING_STAFF"]}>
      <div style={{ display: "flex", minHeight: "100vh", background: "var(--bg)", overflow: "hidden" }}>
        <Sidebar />
        <main style={{ flex: 1, minWidth: 0, overflowX: "hidden", padding: "20px 24px" }}>
          <Topbar />

          <div
            style={{
              marginTop: 24,
              marginBottom: 20,
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-end",
            }}
          >
            <div>
              <h1 style={{ fontSize: 22, fontWeight: 800, color: "var(--text-primary)" }}>
                Billing & Invoices
              </h1>
              <p style={{ fontSize: 13, color: "var(--text-muted)", marginTop: 2 }}>
                Hospital billing ledger, payment integrity, and cash settlement
              </p>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={load} className="btn btn-secondary btn-sm">
                <FaSync style={{ fontSize: 11 }} /> Refresh
              </button>
              <button onClick={() => setShowForm(!showForm)} className="btn btn-primary btn-sm">
                <FaPlus style={{ fontSize: 11 }} /> New Invoice
              </button>
            </div>
          </div>

          {error && (
            <div
              style={{
                marginBottom: 16,
                padding: "12px 16px",
                background: "rgba(244,63,94,0.1)",
                border: "1px solid rgba(244,63,94,0.2)",
                borderRadius: 10,
                color: "#fb7185",
                fontSize: 13,
              }}
            >
              Warning: {error}
            </div>
          )}
          {success && (
            <div
              style={{
                marginBottom: 16,
                padding: "12px 16px",
                background: "rgba(16,185,129,0.1)",
                border: "1px solid rgba(16,185,129,0.2)",
                borderRadius: 10,
                color: "#34d399",
                fontSize: 13,
              }}
            >
              Success: {success}
            </div>
          )}

          {/* KPIs + Pie */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr 1fr 280px",
              gap: 14,
              marginBottom: 20,
            }}
          >
            <div className="kpi-card" style={{ "--kpi-color": "var(--blue)" }}>
              <div className="kpi-icon" style={{ background: "rgba(59,130,246,0.1)" }}>
                <FaMoneyBillWave style={{ color: "var(--blue)" }} />
              </div>
              <div className="kpi-value">${revenueStats.total.toFixed(2)}</div>
              <div className="kpi-label">Total Invoiced</div>
              <div className="kpi-sub">{invoices.length} invoices</div>
            </div>
            <div className="kpi-card" style={{ "--kpi-color": "var(--emerald)" }}>
              <div className="kpi-icon" style={{ background: "rgba(16,185,129,0.1)" }}>
                <FaCheckCircle style={{ color: "var(--emerald)" }} />
              </div>
              <div className="kpi-value">${revenueStats.paid.toFixed(2)}</div>
              <div className="kpi-label">Total Collected</div>
              <div className="kpi-sub">{revenueStats.paidCount} collected</div>
            </div>
            <div className="kpi-card" style={{ "--kpi-color": "var(--amber)" }}>
              <div className="kpi-icon" style={{ background: "rgba(245,158,11,0.1)" }}>
                <FaClock style={{ color: "var(--amber)" }} />
              </div>
              <div className="kpi-value">${revenueStats.pending.toFixed(2)}</div>
              <div className="kpi-label">Outstanding Balance</div>
              <div className="kpi-sub">{revenueStats.pendingCount} pending</div>
            </div>
            <div className="card" style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <ResponsiveContainer width="55%" height={120}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={30}
                    outerRadius={50}
                    paddingAngle={3}
                    dataKey="value"
                  >
                    {pieData.map((e, i) => (
                      <Cell key={i} fill={e.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={CustomTooltipStyle}
                    formatter={(v) => [`$${v.toFixed(2)}`, ""]}
                  />
                </PieChart>
              </ResponsiveContainer>
              <div style={{ flex: 1 }}>
                {pieData.map((d) => (
                  <div
                    key={d.name}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                      marginBottom: 6,
                    }}
                  >
                    <div
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: "50%",
                        background: d.color,
                      }}
                    />
                    <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{d.name}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Create Invoice Form */}
          {showForm && (
            <div className="card" style={{ marginBottom: 20 }}>
              <div
                style={{
                  fontSize: 15,
                  fontWeight: 700,
                  color: "var(--text-primary)",
                  marginBottom: 18,
                }}
              >
                Create & Issue New Patient Invoice
              </div>
              <form onSubmit={handleSubmit}>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                  <div>
                    <label className="hms-label">Patient *</label>
                    <select
                      name="patientId"
                      value={form.patientId}
                      onChange={handleChange}
                      className="hms-select"
                      required
                    >
                      <option value="">Select Patient...</option>
                      {patients.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.fullName} ({p.email})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="hms-label">Appointment (Optional)</label>
                    <select
                      name="appointmentId"
                      value={form.appointmentId}
                      onChange={handleChange}
                      className="hms-select"
                    >
                      <option value="">Select Appointment...</option>
                      {appointments
                        .filter((a) => !form.patientId || a.patientId === form.patientId)
                        .map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.appointmentDate} {a.appointmentTime} — {a.status}
                          </option>
                        ))}
                    </select>
                  </div>
                  <div>
                    <label className="hms-label">Consultation Fee ($)</label>
                    <input
                      type="number"
                      step="0.01"
                      name="consultationFee"
                      value={form.consultationFee}
                      onChange={handleChange}
                      className="hms-input"
                      placeholder="0.00"
                      min="0"
                    />
                  </div>
                  <div>
                    <label className="hms-label">Medicine Fee ($)</label>
                    <input
                      type="number"
                      step="0.01"
                      name="medicineFee"
                      value={form.medicineFee}
                      onChange={handleChange}
                      className="hms-input"
                      placeholder="0.00"
                      min="0"
                    />
                  </div>
                  <div>
                    <label className="hms-label">Lab Fee ($)</label>
                    <input
                      type="number"
                      step="0.01"
                      name="labFee"
                      value={form.labFee}
                      onChange={handleChange}
                      className="hms-input"
                      placeholder="0.00"
                      min="0"
                    />
                  </div>
                  <div>
                    <label className="hms-label">Other Fee ($)</label>
                    <input
                      type="number"
                      step="0.01"
                      name="otherFee"
                      value={form.otherFee}
                      onChange={handleChange}
                      className="hms-input"
                      placeholder="0.00"
                      min="0"
                    />
                  </div>
                </div>

                {/* Total */}
                <div
                  style={{
                    marginTop: 16,
                    padding: "14px 18px",
                    background: "var(--bg-2)",
                    borderRadius: 10,
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    border: "1px solid var(--border)",
                  }}
                >
                  <span style={{ fontSize: 14, color: "var(--text-secondary)" }}>Total Amount</span>
                  <span style={{ fontSize: 24, fontWeight: 800, color: "var(--emerald)" }}>
                    ${totalAmount.toFixed(2)}
                  </span>
                </div>

                <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
                  <button type="submit" disabled={submitting} className="btn btn-primary">
                    {submitting ? "Processing..." : "Generate & Issue Invoice"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowForm(false)}
                    className="btn btn-secondary"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Invoices Table */}
          <div className="card" style={{ padding: 0, overflow: "hidden" }}>
            <div
              style={{
                padding: "14px 20px",
                borderBottom: "1px solid var(--border)",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <span style={{ fontWeight: 700, color: "var(--text-primary)", fontSize: 14 }}>
                Billing Ledger & Invoices
              </span>
              <span className="badge badge-blue">{invoices.length} Total</span>
            </div>
            <div className="table-wrap">
              {loading ? (
                <div style={{ padding: 40, textAlign: "center", color: "var(--text-muted)" }}>
                  Loading billing ledger...
                </div>
              ) : invoices.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state-icon">
                    <FaMoneyBillWave />
                  </div>
                  <div className="empty-state-title">No invoices yet</div>
                  <div className="empty-state-sub">Create your first invoice using the button above</div>
                </div>
              ) : (
                <table className="hms-table">
                  <thead>
                    <tr>
                      <th>Invoice #</th>
                      <th>Patient</th>
                      <th>Total</th>
                      <th>Paid</th>
                      <th>Balance</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td className="font-mono font-bold text-primary">
                          {inv.invoiceNumber || inv.id?.slice(0, 8)}
                        </td>
                        <td>
                          <div
                            style={{
                              fontWeight: 600,
                              color: "var(--text-primary)",
                              fontSize: 13,
                            }}
                          >
                            {inv.patientName || getPatientName(inv.patientId)}
                          </div>
                        </td>
                        <td style={{ fontWeight: 700, color: "var(--text-primary)" }}>
                          ${Number(inv.totalAmount || 0).toFixed(2)}
                        </td>
                        <td className="text-green-600 font-semibold">
                          ${Number(inv.paidAmount || 0).toFixed(2)}
                        </td>
                        <td className="text-amber-600 font-bold">
                          ${Number(inv.outstandingAmount != null ? inv.outstandingAmount : inv.totalAmount || 0).toFixed(2)}
                        </td>
                        <td>{getStatusBadge(inv)}</td>
                        <td>
                          <div style={{ display: "flex", gap: 6 }}>
                            {/* Record Payment button */}
                            {inv.status !== "PAID" && inv.paymentStatus !== "PAID" && inv.status !== "VOID" && (
                              <button
                                onClick={() => handleOpenPaymentModal(inv)}
                                className="btn btn-success btn-sm flex items-center gap-1"
                                title="Collect Payment"
                              >
                                <FaCreditCard style={{ fontSize: 11 }} /> Pay
                              </button>
                            )}
                            {/* Download PDF button */}
                            <button
                              onClick={() => handleDownloadPdf(inv)}
                              className="btn btn-secondary btn-sm flex items-center gap-1"
                              title="Download PDF Invoice"
                            >
                              <FaFilePdf style={{ fontSize: 11, color: "#f43f5e" }} /> PDF
                            </button>
                            {/* Void button */}
                            {inv.status !== "PAID" && inv.status !== "VOID" && (
                              <button
                                onClick={() => handleVoidInvoice(inv)}
                                className="btn btn-ghost btn-sm text-red-500"
                                title="Void Invoice"
                              >
                                <FaBan style={{ fontSize: 11 }} />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

          {/* Record Payment Modal */}
          {paymentModalOpen && activeInvoice && (
            <div className="modal-backdrop">
              <div className="modal-card max-w-md">
                <h3 className="text-lg font-bold mb-3 flex items-center gap-2">
                  <FaMoneyBillWave className="text-green-600" /> Record Patient Payment
                </h3>
                <div className="p-3 bg-gray-50 rounded-lg mb-4 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted">Invoice:</span>
                    <span className="font-mono font-bold">{activeInvoice.invoiceNumber || activeInvoice.id}</span>
                  </div>
                  <div className="flex justify-between mt-1">
                    <span className="text-muted">Outstanding Balance:</span>
                    <span className="font-bold text-amber-600">
                      ${Number(activeInvoice.outstandingAmount != null ? activeInvoice.outstandingAmount : activeInvoice.totalAmount || 0).toFixed(2)}
                    </span>
                  </div>
                </div>

                <form onSubmit={handleRecordPayment}>
                  <div className="space-y-3">
                    <div>
                      <label className="label text-xs">Payment Amount ($) *</label>
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        max={Number(activeInvoice.outstandingAmount != null ? activeInvoice.outstandingAmount : activeInvoice.totalAmount)}
                        required
                        value={paymentForm.amount}
                        onChange={(e) =>
                          setPaymentForm({ ...paymentForm, amount: e.target.value })
                        }
                        className="input w-full font-bold text-lg"
                      />
                    </div>
                    <div>
                      <label className="label text-xs">Payment Method *</label>
                      <select
                        value={paymentForm.paymentMethod}
                        onChange={(e) =>
                          setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })
                        }
                        className="input w-full"
                      >
                        <option value="CASH">Cash Counter</option>
                        <option value="CARD_TERMINAL">Card Terminal (POS)</option>
                        <option value="BANK_TRANSFER">Bank Wire / Transfer</option>
                        <option value="INSURANCE_SETTLEMENT">Insurance Settlement</option>
                      </select>
                    </div>
                    <div>
                      <label className="label text-xs">Transaction Reference / Receipt ID</label>
                      <input
                        type="text"
                        value={paymentForm.transactionReference}
                        onChange={(e) =>
                          setPaymentForm({
                            ...paymentForm,
                            transactionReference: e.target.value,
                          })
                        }
                        placeholder="e.g. POS-98234 / WIRE-10293"
                        className="input w-full font-mono text-sm"
                      />
                    </div>
                    <div>
                      <label className="label text-xs">Notes</label>
                      <input
                        type="text"
                        value={paymentForm.notes}
                        onChange={(e) =>
                          setPaymentForm({ ...paymentForm, notes: e.target.value })
                        }
                        className="input w-full text-sm"
                        placeholder="Optional cashier notes"
                      />
                    </div>
                  </div>

                  <div className="flex justify-end gap-2 mt-6">
                    <button
                      type="button"
                      onClick={() => setPaymentModalOpen(false)}
                      className="btn btn-secondary"
                    >
                      Cancel
                    </button>
                    <button type="submit" disabled={submitting} className="btn btn-success">
                      {submitting ? "Processing..." : "Confirm & Post Payment"}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </main>
      </div>
    </ProtectedRoute>
  );
}
