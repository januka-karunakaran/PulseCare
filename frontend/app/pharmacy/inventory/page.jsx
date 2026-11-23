"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getMedicines, createMedicine } from "@/services/pharmacyService";
import { FaPlus, FaSearch, FaTimes } from "react-icons/fa";

export default function PharmacyInventoryPage() {
  const [medicines, setMedicines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({
    medicineCode: "",
    genericName: "",
    brandName: "",
    strength: "",
    dosageForm: "TABLET",
    dispensingUnit: "tablet",
    packageDescription: "",
    manufacturer: "",
    reorderLevel: 20,
  });

  const loadMedicines = async () => {
    try {
      setLoading(true);
      setError("");
      const res = await getMedicines(search, 0, 50);
      setMedicines(res?.content || []);
    } catch (err) {
      setError(err.message || "Failed to load medicine catalog");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMedicines();
  }, [search]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      setError("");
      await createMedicine(form);
      setSuccess("Medicine added to catalog successfully");
      setShowModal(false);
      setForm({
        medicineCode: "",
        genericName: "",
        brandName: "",
        strength: "",
        dosageForm: "TABLET",
        dispensingUnit: "tablet",
        packageDescription: "",
        manufacturer: "",
        reorderLevel: 20,
      });
      await loadMedicines();
    } catch (err) {
      setError(err.message || "Failed to create medicine");
    }
  };

  return (
    <ProtectedRoute allowedRoles={["PHARMACIST", "ADMIN"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Pharmacy Medicine Catalog" />
          <div className="content-area">
            {error && <div className="alert alert-error mb-4">{error}</div>}
            {success && <div className="alert alert-success mb-4">{success}</div>}

            <div className="card p-4">
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-4">
                <div className="relative flex-1 max-w-md">
                  <input
                    type="text"
                    className="input pl-9 w-full"
                    placeholder="Search by generic or brand name..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                  <FaSearch className="absolute left-3 top-3 text-slate-400" />
                </div>
                <button
                  onClick={() => setShowModal(true)}
                  className="btn btn-primary flex items-center gap-2"
                >
                  <FaPlus /> Add New Medicine
                </button>
              </div>

              {loading ? (
                <div className="py-8 text-center text-slate-500">Loading catalog...</div>
              ) : medicines.length === 0 ? (
                <div className="py-8 text-center text-slate-500">No medicines found.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b text-slate-500">
                        <th className="py-3">Code</th>
                        <th className="py-3">Generic Name</th>
                        <th className="py-3">Brand</th>
                        <th className="py-3">Strength & Form</th>
                        <th className="py-3 text-right">Available Stock</th>
                        <th className="py-3 text-right">Reorder Level</th>
                        <th className="py-3">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {medicines.map((m) => (
                        <tr key={m.id} className="border-b hover:bg-slate-50 dark:hover:bg-slate-800/50">
                          <td className="py-3 font-mono text-xs font-bold">{m.medicineCode}</td>
                          <td className="py-3 font-medium">{m.genericName}</td>
                          <td className="py-3 text-slate-600">{m.brandName || "—"}</td>
                          <td className="py-3">
                            <span className="font-mono text-xs">{m.strength}</span> ({m.dosageForm})
                          </td>
                          <td className="py-3 text-right font-bold">
                            <span className={m.lowStock ? "text-rose-600" : "text-emerald-600"}>
                              {m.totalAvailableStock} {m.dispensingUnit}
                            </span>
                          </td>
                          <td className="py-3 text-right text-slate-500">{m.reorderLevel}</td>
                          <td className="py-3">
                            {m.lowStock ? (
                              <span className="badge badge-warning">Low Stock</span>
                            ) : (
                              <span className="badge badge-success">In Stock</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Modal */}
            {showModal && (
              <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                <div className="bg-white dark:bg-slate-900 rounded-lg max-w-lg w-full p-6 shadow-xl border border-slate-200 dark:border-slate-800">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-lg">Add Medicine to Catalog</h3>
                    <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600">
                      <FaTimes />
                    </button>
                  </div>

                  <form onSubmit={handleSubmit} className="space-y-3 text-sm">
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Code *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          placeholder="MED-001"
                          value={form.medicineCode}
                          onChange={(e) => setForm({ ...form, medicineCode: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Generic Name *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          placeholder="Amoxicillin"
                          value={form.genericName}
                          onChange={(e) => setForm({ ...form, genericName: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Brand Name</label>
                        <input
                          type="text"
                          className="input w-full"
                          placeholder="Amoxil"
                          value={form.brandName}
                          onChange={(e) => setForm({ ...form, brandName: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Strength *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          placeholder="500mg"
                          value={form.strength}
                          onChange={(e) => setForm({ ...form, strength: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Dosage Form</label>
                        <select
                          className="input w-full"
                          value={form.dosageForm}
                          onChange={(e) => setForm({ ...form, dosageForm: e.target.value })}
                        >
                          <option value="TABLET">Tablet</option>
                          <option value="CAPSULE">Capsule</option>
                          <option value="SYRUP">Syrup</option>
                          <option value="INJECTION">Injection</option>
                          <option value="CREAM">Cream</option>
                          <option value="DROPS">Drops</option>
                          <option value="INHALER">Inhaler</option>
                          <option value="OTHER">Other</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Dispensing Unit *</label>
                        <input
                          type="text"
                          required
                          className="input w-full"
                          placeholder="tablet"
                          value={form.dispensingUnit}
                          onChange={(e) => setForm({ ...form, dispensingUnit: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold mb-1">Manufacturer</label>
                        <input
                          type="text"
                          className="input w-full"
                          placeholder="GSK"
                          value={form.manufacturer}
                          onChange={(e) => setForm({ ...form, manufacturer: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold mb-1">Reorder Level</label>
                        <input
                          type="number"
                          className="input w-full"
                          value={form.reorderLevel}
                          onChange={(e) => setForm({ ...form, reorderLevel: parseFloat(e.target.value) || 0 })}
                        />
                      </div>
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowModal(false)}
                        className="btn btn-secondary btn-sm"
                      >
                        Cancel
                      </button>
                      <button type="submit" className="btn btn-primary btn-sm">
                        Save Medicine
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
