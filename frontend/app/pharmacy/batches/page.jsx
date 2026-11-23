"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getMedicines, receiveStock, adjustStock, getBatchesByMedicine } from "@/services/pharmacyService";
import { FaPlus, FaSlidersH, FaTimes } from "react-icons/fa";

export default function PharmacyBatchesPage() {
  const [medicines, setMedicines] = useState([]);
  const [selectedMedId, setSelectedMedId] = useState("");
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [showReceiveModal, setShowReceiveModal] = useState(false);
  const [receiveForm, setReceiveForm] = useState({
    medicineId: "",
    batchNumber: "",
    supplierReference: "",
    expiryDate: "",
    quantityReceived: 100,
    stockUnit: "tablet",
    storageLocation: "Shelf A-1",
  });

  const [showAdjustModal, setShowAdjustModal] = useState(false);
  const [adjustForm, setAdjustForm] = useState({
    batchId: "",
    adjustedQuantity: 0,
    reasonCode: "AUDIT_CORRECTION",
    notes: "",
  });

  useEffect(() => {
    const loadMeds = async () => {
      try {
        const res = await getMedicines("", 0, 100);
        const list = res?.content || [];
        setMedicines(list);
        if (list.length > 0) {
          setSelectedMedId(list[0].id);
          setReceiveForm((prev) => ({ ...prev, medicineId: list[0].id, stockUnit: list[0].dispensingUnit || "tablet" }));
        }
      } catch (err) {
        setError(err.message || "Failed to load medicines");
      }
    };
    loadMeds();
  }, []);

  const loadBatches = async (medId) => {
    if (!medId) return;
    try {
      setLoading(true);
      setError("");
      const res = await getBatchesByMedicine(medId);
      setBatches(res || []);
    } catch (err) {
      setError(err.message || "Failed to load batches");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (selectedMedId) {
      loadBatches(selectedMedId);
    }
  }, [selectedMedId]);

  const handleReceiveSubmit = async (e) => {
    e.preventDefault();
    try {
      setError("");
      await receiveStock({
        ...receiveForm,
        expiryDate: new Date(receiveForm.expiryDate).toISOString(),
        quantityReceived: parseFloat(receiveForm.quantityReceived),
        idempotencyKey: `rec-${receiveForm.medicineId}-${receiveForm.batchNumber}-${Date.now()}`,
      });
      setSuccess("Stock received successfully!");
      setShowReceiveModal(false);
      await loadBatches(selectedMedId);
    } catch (err) {
      setError(err.message || "Failed to receive stock");
    }
  };

  const handleAdjustSubmit = async (e) => {
    e.preventDefault();
    try {
      setError("");
      await adjustStock({
        ...adjustForm,
        adjustedQuantity: parseFloat(adjustForm.adjustedQuantity),
      });
      setSuccess("Batch stock adjusted successfully!");
      setShowAdjustModal(false);
      await loadBatches(selectedMedId);
    } catch (err) {
      setError(err.message || "Failed to adjust stock");
    }
  };

  return (
    <ProtectedRoute allowedRoles={["PHARMACIST", "ADMIN"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Pharmacy Batch & Stock Management" />
          <div className="content-area">
            {error && <div className="alert alert-error mb-4">{error}</div>}
            {success && <div className="alert alert-success mb-4">{success}</div>}

            <div className="card p-4">
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
                <div className="flex items-center gap-3 w-full md:w-auto">
                  <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Select Medicine:</label>
                  <select
                    className="input text-sm"
                    value={selectedMedId}
                    onChange={(e) => setSelectedMedId(e.target.value)}
                  >
                    {medicines.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.genericName} ({m.strength}) - {m.medicineCode}
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  onClick={() => setShowReceiveModal(true)}
                  className="btn btn-primary flex items-center gap-2"
                >
                  <FaPlus /> Receive New Batch
                </button>
              </div>

              {loading ? (
                <div className="py-8 text-center text-slate-500">Loading batches...</div>
              ) : batches.length === 0 ? (
                <div className="py-8 text-center text-slate-500">No batches on record for this medicine.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b text-slate-500">
                        <th className="py-3">Batch Number</th>
                        <th className="py-3">Location</th>
                        <th className="py-3 text-right">Available Qty</th>
                        <th className="py-3 text-right">Received Qty</th>
                        <th className="py-3">Expiry Date</th>
                        <th className="py-3">Status</th>
                        <th className="py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {batches.map((b) => (
                        <tr key={b.id} className="border-b hover:bg-slate-50 dark:hover:bg-slate-800/50">
                          <td className="py-3 font-mono font-bold text-xs">{b.batchNumber}</td>
                          <td className="py-3 text-slate-600">{b.storageLocation || "—"}</td>
                          <td className="py-3 text-right font-bold text-emerald-600">
                            {b.quantityAvailable} {b.stockUnit}
                          </td>
                          <td className="py-3 text-right text-slate-500">{b.quantityReceived}</td>
                          <td className="py-3">
                            <span className={b.nearExpiry ? "text-rose-600 font-bold" : ""}>
                              {b.expiryDate ? b.expiryDate.substring(0, 10) : "N/A"}
                            </span>
                          </td>
                          <td className="py-3">
                            <span className="badge badge-success">{b.status}</span>
                          </td>
                          <td className="py-3 text-right">
                            <button
                              onClick={() => {
                                setAdjustForm({
                                  batchId: b.id,
                                  adjustedQuantity: b.quantityAvailable,
                                  reasonCode: "AUDIT_CORRECTION",
                                  notes: "",
                                });
                                setShowAdjustModal(true);
                              }}
                              className="btn btn-secondary btn-xs flex items-center gap-1 ml-auto"
                            >
                              <FaSlidersH /> Adjust
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Receive Stock Modal */}
            {showReceiveModal && (
              <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-slate-900 rounded-lg max-w-lg w-full p-6 shadow-xl border border-slate-200 dark:border-slate-800">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-lg">Receive Stock (Goods Receipt)</h3>
                    <button onClick={() => setShowReceiveModal(false)} className="text-slate-400 hover:text-slate-600">
                      <FaTimes />
                    </button>
                  </div>

                  <form onSubmit={handleReceiveSubmit} className="space-y-3 text-sm">
                    <div>
                      <label className="block text-xs font-semibold mb-1">Target Medicine *</label>
                      <select
                        className="input w-full"
                        value={receiveForm.medicineId}
                        onChange={(e) => setReceiveForm({ ...receiveForm, medicineId: e.target.value })}
                      >
                        {medicines.map((m) => (
                          <option key={m.id} value={m.id}>
                            {m.genericName} ({m.strength})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Batch / Lot Number *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          placeholder="LOT-2026-X1"
                          value={receiveForm.batchNumber}
                          onChange={(e) => setReceiveForm({ ...receiveForm, batchNumber: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Supplier Ref / PO</label>
                        <input
                          type="text"
                          className="input w-full"
                          placeholder="PO-8849"
                          value={receiveForm.supplierReference}
                          onChange={(e) => setReceiveForm({ ...receiveForm, supplierReference: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Expiry Date *</label>
                        <input
                          type="date"
                          required
                          className="input w-full"
                          value={receiveForm.expiryDate}
                          onChange={(e) => setReceiveForm({ ...receiveForm, expiryDate: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Quantity Received *</label>
                        <input
                          type="number"
                          required
                          min="1"
                          className="input w-full"
                          value={receiveForm.quantityReceived}
                          onChange={(e) => setReceiveForm({ ...receiveForm, quantityReceived: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Stock Unit *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          value={receiveForm.stockUnit}
                          onChange={(e) => setReceiveForm({ ...receiveForm, stockUnit: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Storage Location</label>
                        <input
                          type="text"
                          className="input w-full"
                          value={receiveForm.storageLocation}
                          onChange={(e) => setReceiveForm({ ...receiveForm, storageLocation: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowReceiveModal(false)}
                        className="btn btn-secondary btn-sm"
                      >
                        Cancel
                      </button>
                      <button type="submit" className="btn btn-primary btn-sm">
                        Confirm Stock Receipt
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* Adjust Stock Modal */}
            {showAdjustModal && (
              <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-slate-900 rounded-lg max-w-sm w-full p-6 shadow-xl border border-slate-200 dark:border-slate-800">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-lg">Adjust Batch Stock</h3>
                    <button onClick={() => setShowAdjustModal(false)} className="text-slate-400 hover:text-slate-600">
                      <FaTimes />
                    </button>
                  </div>

                  <form onSubmit={handleAdjustSubmit} className="space-y-3 text-sm">
                    <div>
                      <label className="block text-xs font-semibold mb-1">New Available Quantity *</label>
                      <input
                        type="number"
                        required
                        min="0"
                        className="input w-full"
                        value={adjustForm.adjustedQuantity}
                        onChange={(e) => setAdjustForm({ ...adjustForm, adjustedQuantity: e.target.value })}
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold mb-1">Reason Code *</label>
                      <select
                        className="input w-full"
                        value={adjustForm.reasonCode}
                        onChange={(e) => setAdjustForm({ ...adjustForm, reasonCode: e.target.value })}
                      >
                        <option value="AUDIT_CORRECTION">Audit Reconciliation</option>
                        <option value="DAMAGED">Damaged Goods</option>
                        <option value="EXPIRED">Expired Stock</option>
                        <option value="DISPENSE_CORRECTION">Dispense Correction</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold mb-1">Notes</label>
                      <textarea
                        className="input w-full"
                        rows="2"
                        value={adjustForm.notes}
                        onChange={(e) => setAdjustForm({ ...adjustForm, notes: e.target.value })}
                        placeholder="Reason for adjustment..."
                      />
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowAdjustModal(false)}
                        className="btn btn-secondary btn-sm"
                      >
                        Cancel
                      </button>
                      <button type="submit" className="btn btn-primary btn-sm">
                        Save Adjustment
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
