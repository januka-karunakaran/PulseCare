"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { searchLabTests, createLabTest, deactivateLabTest, updateLabTest } from "@/services/labService";
import { FaFlask, FaPlus, FaCheck, FaBan, FaSearch } from "react-icons/fa";

export default function AdminLabTests() {
  const [tests, setTests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [showAddModal, setShowAddModal] = useState(false);

  const [form, setForm] = useState({
    testCode: "",
    testName: "",
    category: "Hematology",
    specimenType: "Whole Blood",
    sampleVolume: "3 mL",
    collectionInstructions: "Standard venipuncture in EDTA tube",
    defaultUnit: "",
    referenceRangeMetadata: "",
    description: "",
  });

  const [submitting, setSubmitting] = useState(false);

  const loadTests = async () => {
    try {
      setLoading(true);
      const data = await searchLabTests(search, 0, 50);
      setTests(data.content || []);
    } catch (err) {
      setError(err.message || "Failed to load laboratory test definitions");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTests();
  }, [search]);

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      setSubmitting(true);
      await createLabTest(form);
      setShowAddModal(false);
      setForm({
        testCode: "",
        testName: "",
        category: "Hematology",
        specimenType: "Whole Blood",
        sampleVolume: "3 mL",
        collectionInstructions: "Standard venipuncture in EDTA tube",
        defaultUnit: "",
        referenceRangeMetadata: "",
        description: "",
      });
      await loadTests();
    } catch (err) {
      alert("Error creating test: " + err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (test) => {
    try {
      if (test.active) {
        if (!confirm(`Deactivate ${test.testName}? It will no longer be available for new orders.`)) return;
        await deactivateLabTest(test.id);
      } else {
        await updateLabTest(test.id, { active: true });
      }
      await loadTests();
    } catch (err) {
      alert("Error updating status: " + err.message);
    }
  };

  return (
    <ProtectedRoute allowedRoles={["ADMIN"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Laboratory Test Catalog Management" />
          <div className="content-area">
            {/* Header controls */}
            <div className="card p-4 mb-6 flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-2 max-w-sm w-full">
                <div className="relative w-full">
                  <FaSearch className="absolute left-3 top-3 text-slate-400 text-xs" />
                  <input
                    type="text"
                    placeholder="Search test catalog by name or code..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="hms-input pl-9 text-xs"
                  />
                </div>
              </div>
              <button onClick={() => setShowAddModal(true)} className="btn btn-sm btn-primary">
                <FaPlus className="mr-1" /> Add Catalog Test
              </button>
            </div>

            {/* Test Catalog Table */}
            <div className="card p-6">
              <h3 className="font-bold text-lg text-slate-800 dark:text-slate-100 mb-4">Laboratory Catalog Definitions</h3>
              {loading ? (
                <div className="py-12 text-center text-slate-500">Loading catalog...</div>
              ) : error ? (
                <div className="py-8 text-center text-rose-500">{error}</div>
              ) : tests.length === 0 ? (
                <div className="py-12 text-center text-slate-400">No laboratory tests found.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="table-default w-full">
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Test Name</th>
                        <th>Category</th>
                        <th>Specimen</th>
                        <th>Default Unit</th>
                        <th>Reference Range</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {tests.map((t) => (
                        <tr key={t.id}>
                          <td className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">{t.testCode}</td>
                          <td className="font-semibold">{t.testName}</td>
                          <td>
                            <span className="badge badge-secondary text-xs">{t.category}</span>
                          </td>
                          <td className="text-xs text-slate-500">{t.specimenType}</td>
                          <td className="font-mono text-xs">{t.defaultUnit || "-"}</td>
                          <td className="text-xs text-slate-500">{t.referenceRangeMetadata || "Standard"}</td>
                          <td>
                            <span className={`badge text-xs ${t.active ? "badge-success" : "badge-secondary"}`}>
                              {t.active ? "ACTIVE" : "INACTIVE"}
                            </span>
                          </td>
                          <td>
                            <button
                              onClick={() => handleToggleActive(t)}
                              className={`btn btn-2xs ${t.active ? "btn-danger" : "btn-success"}`}
                            >
                              {t.active ? (
                                <>
                                  <FaBan className="mr-1" /> Deactivate
                                </>
                              ) : (
                                <>
                                  <FaCheck className="mr-1" /> Activate
                                </>
                              )}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Add Test Modal */}
            {showAddModal && (
              <div className="modal-backdrop">
                <div className="modal-card max-w-lg p-6 bg-white dark:bg-slate-900 rounded-2xl shadow-xl">
                  <h3 className="font-bold text-lg mb-4 text-slate-800 dark:text-slate-100">Add New Laboratory Test</h3>
                  <form onSubmit={handleCreate} className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="hms-label text-xs">Test Code *</label>
                        <input
                          type="text"
                          required
                          value={form.testCode}
                          onChange={(e) => setForm({ ...form, testCode: e.target.value.toUpperCase() })}
                          className="hms-input text-xs font-mono uppercase"
                          placeholder="e.g. HBA1C"
                        />
                      </div>
                      <div>
                        <label className="hms-label text-xs">Test Name *</label>
                        <input
                          type="text"
                          required
                          value={form.testName}
                          onChange={(e) => setForm({ ...form, testName: e.target.value })}
                          className="hms-input text-xs"
                          placeholder="e.g. Hemoglobin A1c"
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="hms-label text-xs">Category</label>
                        <select
                          value={form.category}
                          onChange={(e) => setForm({ ...form, category: e.target.value })}
                          className="hms-input text-xs"
                        >
                          <option value="Hematology">Hematology</option>
                          <option value="Biochemistry">Biochemistry</option>
                          <option value="Microbiology">Microbiology</option>
                          <option value="Urinalysis">Urinalysis</option>
                          <option value="Immunology">Immunology</option>
                        </select>
                      </div>
                      <div>
                        <label className="hms-label text-xs">Specimen Type</label>
                        <select
                          value={form.specimenType}
                          onChange={(e) => setForm({ ...form, specimenType: e.target.value })}
                          className="hms-input text-xs"
                        >
                          <option value="Whole Blood">Whole Blood</option>
                          <option value="Serum">Serum</option>
                          <option value="Plasma">Plasma</option>
                          <option value="Urine">Urine</option>
                          <option value="Nasopharyngeal Swab">Nasopharyngeal Swab</option>
                        </select>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="hms-label text-xs">Default Unit</label>
                        <input
                          type="text"
                          value={form.defaultUnit}
                          onChange={(e) => setForm({ ...form, defaultUnit: e.target.value })}
                          className="hms-input text-xs font-mono"
                          placeholder="e.g. % or mg/dL"
                        />
                      </div>
                      <div>
                        <label className="hms-label text-xs">Reference Range</label>
                        <input
                          type="text"
                          value={form.referenceRangeMetadata}
                          onChange={(e) => setForm({ ...form, referenceRangeMetadata: e.target.value })}
                          className="hms-input text-xs"
                          placeholder="e.g. < 5.7 %"
                        />
                      </div>
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowAddModal(false)}
                        className="btn btn-sm btn-secondary"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="btn btn-sm btn-primary"
                      >
                        Save Catalog Entry
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
