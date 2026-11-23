"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getPrescriptionQueue, dispenseMedication } from "@/services/pharmacyService";
import { FaPrescriptionBottleAlt, FaCheckCircle, FaExclamationCircle, FaTimes } from "react-icons/fa";

export default function PharmacyPrescriptionsPage() {
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [selectedPrescription, setSelectedPrescription] = useState(null);
  const [dispenseForm, setDispenseForm] = useState({ notes: "" });
  const [submitting, setSubmitting] = useState(false);

  const loadQueue = async () => {
    try {
      setLoading(true);
      setError("");
      const res = await getPrescriptionQueue(0, 50);
      setQueue(res?.content || []);
    } catch (err) {
      setError(err.message || "Failed to load prescription queue");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadQueue();
  }, []);

  const handleOpenDispense = (prescription) => {
    setSelectedPrescription(prescription);
    setDispenseForm({ notes: "" });
  };

  const handleDispenseSubmit = async (e) => {
    e.preventDefault();
    if (!selectedPrescription) return;

    try {
      setSubmitting(true);
      setError("");

      const items = selectedPrescription.medicines
        .filter((m) => m.medicineId && m.remainingQuantity > 0)
        .map((m) => ({
          prescriptionLineId: m.medicineId,
          medicineId: m.medicineId,
          requestedQuantity: m.remainingQuantity,
          dispenseUnit: "unit",
          notes: "Prescription dispense",
        }));

      if (items.length === 0) {
        throw new Error("No eligible prescription items found with catalog mapping");
      }

      await dispenseMedication({
        prescriptionId: selectedPrescription.prescriptionId,
        idempotencyKey: `disp-${selectedPrescription.prescriptionId}-${Date.now()}`,
        items,
        notes: dispenseForm.notes,
      });

      setSuccess("Medication dispensed successfully!");
      setSelectedPrescription(null);
      await loadQueue();
    } catch (err) {
      setError(err.message || "Failed to dispense medication");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ProtectedRoute allowedRoles={["PHARMACIST", "ADMIN"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Pharmacy Prescription Queue & Dispensing" />
          <div className="content-area">
            {error && <div className="alert alert-error mb-4">{error}</div>}
            {success && <div className="alert alert-success mb-4">{success}</div>}

            <div className="card p-4">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-bold text-lg text-slate-800 dark:text-slate-100 flex items-center gap-2">
                  <FaPrescriptionBottleAlt className="text-blue-500" /> Prescriptions Awaiting Dispensation
                </h3>
                <button onClick={loadQueue} className="btn btn-secondary btn-sm">
                  Refresh Queue
                </button>
              </div>

              {loading ? (
                <div className="py-8 text-center text-slate-500">Loading prescription queue...</div>
              ) : queue.length === 0 ? (
                <div className="py-8 text-center text-slate-500">No prescriptions found in queue.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b text-slate-500">
                        <th className="py-3">Prescription Date</th>
                        <th className="py-3">Patient</th>
                        <th className="py-3">Doctor</th>
                        <th className="py-3">Medicines & Availability</th>
                        <th className="py-3">Status</th>
                        <th className="py-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {queue.map((item) => (
                        <tr key={item.prescriptionId} className="border-b hover:bg-slate-50 dark:hover:bg-slate-800/50">
                          <td className="py-3 font-mono text-xs">
                            {item.prescribedAt ? item.prescribedAt.substring(0, 10) : "N/A"}
                          </td>
                          <td className="py-3 font-medium">{item.patientName}</td>
                          <td className="py-3 text-slate-600">{item.doctorName}</td>
                          <td className="py-3">
                            <div className="flex flex-col gap-1">
                              {item.medicines?.map((med, idx) => (
                                <div key={idx} className="flex items-center gap-2 text-xs">
                                  <span>{med.medicineName || med.rawText}</span>
                                  {med.stockStatus === "AVAILABLE" && (
                                    <span className="badge badge-success text-[10px]">In Stock ({med.totalAvailableStock})</span>
                                  )}
                                  {med.stockStatus === "LOW_STOCK" && (
                                    <span className="badge badge-warning text-[10px]">Low ({med.totalAvailableStock})</span>
                                  )}
                                  {med.stockStatus === "OUT_OF_STOCK" && (
                                    <span className="badge badge-error text-[10px]">Out of Stock</span>
                                  )}
                                  {med.stockStatus === "NOT_IN_CATALOG" && (
                                    <span className="badge badge-neutral text-[10px]">Unmapped</span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </td>
                          <td className="py-3">
                            {item.fullyDispensed ? (
                              <span className="badge badge-success flex items-center gap-1 w-max">
                                <FaCheckCircle /> Dispensed
                              </span>
                            ) : (
                              <span className="badge badge-warning flex items-center gap-1 w-max">
                                <FaExclamationCircle /> Pending
                              </span>
                            )}
                          </td>
                          <td className="py-3 text-right">
                            {!item.fullyDispensed && (
                              <button
                                onClick={() => handleOpenDispense(item)}
                                className="btn btn-primary btn-sm"
                              >
                                Dispense
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Dispense Modal */}
            {selectedPrescription && (
              <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-slate-900 rounded-lg max-w-lg w-full p-6 shadow-xl border border-slate-200 dark:border-slate-800">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-lg">Dispense Prescription</h3>
                    <button onClick={() => setSelectedPrescription(null)} className="text-slate-400 hover:text-slate-600">
                      <FaTimes />
                    </button>
                  </div>

                  <div className="mb-4 text-sm">
                    <div>
                      <span className="font-semibold">Patient:</span> {selectedPrescription.patientName}
                    </div>
                    <div>
                      <span className="font-semibold">Doctor:</span> {selectedPrescription.doctorName}
                    </div>
                  </div>

                  <div className="mb-4">
                    <div className="font-semibold text-xs text-slate-500 uppercase tracking-wider mb-2">
                      Medications to Allocate:
                    </div>
                    <div className="border rounded divide-y text-sm">
                      {selectedPrescription.medicines?.map((m, idx) => (
                        <div key={idx} className="p-2 flex justify-between items-center">
                          <div>
                            <div className="font-medium">{m.medicineName || m.rawText}</div>
                            <div className="text-xs text-slate-500">
                              Prescribed: {m.prescribedQuantity} | Available Stock: {m.totalAvailableStock}
                            </div>
                          </div>
                          <span
                            className={`badge ${
                              m.totalAvailableStock >= m.remainingQuantity
                                ? "badge-success"
                                : "badge-error"
                            }`}
                          >
                            {m.totalAvailableStock >= m.remainingQuantity ? "Ready" : "Stock Shortage"}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <form onSubmit={handleDispenseSubmit}>
                    <div className="mb-4">
                      <label className="block text-xs font-semibold mb-1">Pharmacist Notes</label>
                      <textarea
                        className="input w-full text-sm"
                        rows="2"
                        value={dispenseForm.notes}
                        onChange={(e) => setDispenseForm({ ...dispenseForm, notes: e.target.value })}
                        placeholder="Dispensing verification notes..."
                      />
                    </div>

                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => setSelectedPrescription(null)}
                        className="btn btn-secondary btn-sm"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="btn btn-primary btn-sm flex items-center gap-1"
                      >
                        {submitting ? "Allocating Stock..." : "Confirm & Dispense"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
