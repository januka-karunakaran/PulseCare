"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getLabOrders,
  collectSpecimen,
  receiveSpecimen,
  rejectSpecimen,
  enterLabResult,
  verifyLabResult,
  releaseLabResult,
  downloadLabReportPdf,
} from "@/services/labService";
import { getRole } from "@/utils/auth";
import { FaFlask, FaVial, FaCheck, FaFilePdf, FaTimesCircle, FaCheckDouble } from "react-icons/fa";

export default function LabOrdersQueue() {
  const role = getRole();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  // Modals
  const [activeOrder, setActiveOrder] = useState(null);
  const [showSpecimenModal, setShowSpecimenModal] = useState(false);
  const [specimenType, setSpecimenType] = useState("Blood");
  const [specimenNotes, setSpecimenNotes] = useState("");

  const [showResultModal, setShowResultModal] = useState(false);
  const [selectedTest, setSelectedTest] = useState(null);
  const [resultType, setResultType] = useState("NUMERIC");
  const [numericValue, setNumericValue] = useState("");
  const [textValue, setTextValue] = useState("");
  const [interpretation, setInterpretation] = useState("NORMAL");
  const [resultNotes, setResultNotes] = useState("");

  const [actionLoading, setActionLoading] = useState(false);

  const fetchOrders = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getLabOrders(statusFilter, 0, 50);
      setOrders(data.content || []);
    } catch (err) {
      setError(err.message || "Failed to load laboratory orders");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [statusFilter]);

  const handleCollectSpecimen = async (e) => {
    e.preventDefault();
    if (!activeOrder) return;
    try {
      setActionLoading(true);
      await collectSpecimen(activeOrder.id, {
        specimenType,
        notes: specimenNotes,
      });
      setShowSpecimenModal(false);
      setSpecimenNotes("");
      await fetchOrders();
    } catch (err) {
      alert("Error: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleEnterResult = async (e) => {
    e.preventDefault();
    if (!activeOrder || !selectedTest) return;
    try {
      setActionLoading(true);
      await enterLabResult(activeOrder.id, {
        testCode: selectedTest.testCode,
        specimenId: activeOrder.specimens?.[0]?.id,
        resultType,
        numericValue: resultType === "NUMERIC" ? Number(numericValue) : null,
        textValue: resultType === "TEXT" ? textValue : null,
        unit: selectedTest.defaultUnit,
        referenceRange: selectedTest.referenceRange,
        interpretation,
        notes: resultNotes,
        saveAsDraft: false,
      });
      setShowResultModal(false);
      setNumericValue("");
      setTextValue("");
      setResultNotes("");
      await fetchOrders();
    } catch (err) {
      alert("Error saving result: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleVerify = async (resultId) => {
    if (!confirm("Are you sure you want to verify this laboratory result?")) return;
    try {
      setActionLoading(true);
      await verifyLabResult(resultId);
      await fetchOrders();
    } catch (err) {
      alert("Verification failed: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  const handleRelease = async (resultId) => {
    if (!confirm("Release this laboratory report to the patient medical record?")) return;
    try {
      setActionLoading(true);
      await releaseLabResult(resultId);
      await fetchOrders();
    } catch (err) {
      alert("Release failed: " + err.message);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <ProtectedRoute allowedRoles={["LAB_TECH", "ADMIN", "DOCTOR"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Laboratory Orders & Specimen Work Queue" />
          <div className="content-area">
            {/* Filter Bar */}
            <div className="card p-4 mb-6 flex flex-wrap items-center justify-between gap-4">
              <div className="flex gap-2">
                {["", "ORDERED", "IN_PROGRESS", "COMPLETED", "CANCELLED"].map((st) => (
                  <button
                    key={st}
                    onClick={() => setStatusFilter(st)}
                    className={`btn btn-sm ${statusFilter === st ? "btn-primary" : "btn-outline"}`}
                  >
                    {st === "" ? "All Orders" : st}
                  </button>
                ))}
              </div>
              <button onClick={fetchOrders} className="btn btn-sm btn-secondary">
                Refresh Queue
              </button>
            </div>

            {/* Orders Table */}
            <div className="card p-6">
              {loading ? (
                <div className="py-12 text-center text-slate-500">Loading laboratory work queue...</div>
              ) : error ? (
                <div className="py-8 text-center text-rose-500">{error}</div>
              ) : orders.length === 0 ? (
                <div className="py-12 text-center text-slate-400">No laboratory orders in this view.</div>
              ) : (
                <div className="space-y-4">
                  {orders.map((order) => (
                    <div
                      key={order.id}
                      className="p-4 border rounded-xl bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                        <div className="flex items-center gap-3">
                          <span className="font-mono text-sm font-bold text-blue-600 dark:text-blue-400">
                            {order.orderNumber}
                          </span>
                          <span
                            className={`badge text-xs ${
                              order.priority === "STAT"
                                ? "badge-danger"
                                : order.priority === "URGENT"
                                ? "badge-warning"
                                : "badge-primary"
                            }`}
                          >
                            {order.priority}
                          </span>
                          <span className="badge badge-info text-xs">{order.status}</span>
                        </div>
                        <div className="text-xs text-slate-500">
                          Patient: <strong className="text-slate-800 dark:text-slate-200">{order.patientName}</strong> | Doctor: {order.doctorName}
                        </div>
                      </div>

                      {/* Tests List & Specimens */}
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-2 pt-2 border-t border-slate-100 dark:border-slate-800">
                        <div>
                          <div className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">
                            Ordered Tests & Results
                          </div>
                          <div className="space-y-1">
                            {order.orderedTests?.map((test) => {
                              const res = order.results?.find((r) => r.testCode === test.testCode);
                              return (
                                <div
                                  key={test.testCode}
                                  className="flex items-center justify-between p-2 rounded bg-slate-50 dark:bg-slate-800/60 text-xs"
                                >
                                  <div>
                                    <span className="font-semibold text-slate-800 dark:text-slate-100">{test.testName}</span>
                                    <span className="text-slate-400 ml-1">({test.testCode})</span>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    {res ? (
                                      <>
                                        <span className="font-mono font-bold text-blue-600 dark:text-blue-400">
                                          {res.numericValue ?? res.textValue} {res.unit}
                                        </span>
                                        <span className={`badge text-2xs ${res.interpretation === "NORMAL" ? "badge-success" : "badge-warning"}`}>
                                          {res.interpretation}
                                        </span>
                                        <span className="badge badge-secondary text-2xs">{res.workflowStatus}</span>

                                        {/* Verification & Release Buttons for Doctor/Admin */}
                                        {(role === "DOCTOR" || role === "ADMIN") && res.workflowStatus === "ENTERED" && (
                                          <button
                                            onClick={() => handleVerify(res.id)}
                                            className="btn btn-2xs btn-warning"
                                            disabled={actionLoading}
                                          >
                                            <FaCheck className="mr-1" /> Verify
                                          </button>
                                        )}
                                        {(role === "DOCTOR" || role === "ADMIN") && res.workflowStatus === "VERIFIED" && (
                                          <button
                                            onClick={() => handleRelease(res.id)}
                                            className="btn btn-2xs btn-success"
                                            disabled={actionLoading}
                                          >
                                            <FaCheckDouble className="mr-1" /> Release
                                          </button>
                                        )}
                                        {res.workflowStatus === "RELEASED" && (
                                          <button
                                            onClick={() => downloadLabReportPdf(res.id)}
                                            className="btn btn-2xs btn-outline"
                                            title="Download Official PDF Report"
                                          >
                                            <FaFilePdf className="text-rose-500" />
                                          </button>
                                        )}
                                      </>
                                    ) : (
                                      <button
                                        onClick={() => {
                                          setActiveOrder(order);
                                          setSelectedTest(test);
                                          setShowResultModal(true);
                                        }}
                                        className="btn btn-2xs btn-primary"
                                        disabled={order.status === "CANCELLED"}
                                      >
                                        Enter Result
                                      </button>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>

                        <div>
                          <div className="flex justify-between items-center mb-1">
                            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Specimen Tracking</span>
                            {order.status !== "CANCELLED" && (
                              <button
                                onClick={() => {
                                  setActiveOrder(order);
                                  setShowSpecimenModal(true);
                                }}
                                className="btn btn-2xs btn-outline"
                              >
                                <FaVial className="mr-1" /> Collect Specimen
                              </button>
                            )}
                          </div>
                          {order.specimens?.length === 0 ? (
                            <div className="text-xs text-slate-400 italic p-2 bg-slate-50 dark:bg-slate-800/40 rounded">
                              No specimen collected yet.
                            </div>
                          ) : (
                            <div className="space-y-1">
                              {order.specimens?.map((spec) => (
                                <div
                                  key={spec.id}
                                  className="flex items-center justify-between p-2 rounded bg-slate-50 dark:bg-slate-800/60 text-xs"
                                >
                                  <div>
                                    <span className="font-mono font-semibold">{spec.specimenNumber}</span>
                                    <span className="text-slate-500 ml-1">({spec.specimenType})</span>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <span className={`badge text-2xs ${spec.collectionStatus === "COLLECTED" ? "badge-info" : spec.collectionStatus === "RECEIVED" ? "badge-success" : "badge-danger"}`}>
                                      {spec.collectionStatus}
                                    </span>
                                    {spec.collectionStatus === "COLLECTED" && (
                                      <button
                                        onClick={async () => {
                                          await receiveSpecimen(spec.id);
                                          fetchOrders();
                                        }}
                                        className="btn btn-2xs btn-success"
                                      >
                                        Receive
                                      </button>
                                    )}
                                    {spec.collectionStatus !== "REJECTED" && (
                                      <button
                                        onClick={async () => {
                                          const reason = prompt("Enter rejection reason:");
                                          if (reason) {
                                            await rejectSpecimen(spec.id, reason);
                                            fetchOrders();
                                          }
                                        }}
                                        className="btn btn-2xs btn-danger"
                                      >
                                        Reject
                                      </button>
                                    )}
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Specimen Modal */}
            {showSpecimenModal && (
              <div className="modal-backdrop">
                <div className="modal-card max-w-md p-6 bg-white dark:bg-slate-900 rounded-2xl shadow-xl">
                  <h3 className="font-bold text-lg mb-4 text-slate-800 dark:text-slate-100">Record Specimen Collection</h3>
                  <form onSubmit={handleCollectSpecimen} className="space-y-4">
                    <div>
                      <label className="hms-label text-xs">Specimen Type</label>
                      <select
                        value={specimenType}
                        onChange={(e) => setSpecimenType(e.target.value)}
                        className="hms-input text-sm"
                      >
                        <option value="Whole Blood">Whole Blood</option>
                        <option value="Serum">Serum</option>
                        <option value="Plasma">Plasma</option>
                        <option value="Urine">Urine</option>
                        <option value="Nasopharyngeal Swab">Nasopharyngeal Swab</option>
                        <option value="Sputum">Sputum</option>
                      </select>
                    </div>
                    <div>
                      <label className="hms-label text-xs">Collection Notes</label>
                      <textarea
                        value={specimenNotes}
                        onChange={(e) => setSpecimenNotes(e.target.value)}
                        className="hms-input text-sm"
                        placeholder="e.g. Venipuncture left arm, fasting confirmed"
                        rows={3}
                      />
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowSpecimenModal(false)}
                        className="btn btn-sm btn-secondary"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={actionLoading}
                        className="btn btn-sm btn-primary"
                      >
                        Confirm Collection
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* Result Entry Modal */}
            {showResultModal && selectedTest && (
              <div className="modal-backdrop">
                <div className="modal-card max-w-lg p-6 bg-white dark:bg-slate-900 rounded-2xl shadow-xl">
                  <h3 className="font-bold text-lg mb-1 text-slate-800 dark:text-slate-100">
                    Record Laboratory Result
                  </h3>
                  <div className="text-xs text-slate-500 mb-4">
                    Test: <strong>{selectedTest.testName}</strong> ({selectedTest.testCode})
                  </div>
                  <form onSubmit={handleEnterResult} className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="hms-label text-xs">Result Type</label>
                        <select
                          value={resultType}
                          onChange={(e) => setResultType(e.target.value)}
                          className="hms-input text-sm"
                        >
                          <option value="NUMERIC">Numeric</option>
                          <option value="TEXT">Textual / Qualitative</option>
                        </select>
                      </div>
                      <div>
                        <label className="hms-label text-xs">Clinical Interpretation</label>
                        <select
                          value={interpretation}
                          onChange={(e) => setInterpretation(e.target.value)}
                          className="hms-input text-sm"
                        >
                          <option value="NORMAL">NORMAL</option>
                          <option value="ABNORMAL">ABNORMAL</option>
                          <option value="CRITICAL">CRITICAL</option>
                          <option value="INDETERMINATE">INDETERMINATE</option>
                        </select>
                      </div>
                    </div>

                    {resultType === "NUMERIC" ? (
                      <div>
                        <label className="hms-label text-xs">
                          Numeric Value ({selectedTest.defaultUnit || "Unit"})
                        </label>
                        <input
                          type="number"
                          step="any"
                          required
                          value={numericValue}
                          onChange={(e) => setNumericValue(e.target.value)}
                          className="hms-input text-sm"
                          placeholder="e.g. 5.4"
                        />
                      </div>
                    ) : (
                      <div>
                        <label className="hms-label text-xs">Text Result</label>
                        <input
                          type="text"
                          required
                          value={textValue}
                          onChange={(e) => setTextValue(e.target.value)}
                          className="hms-input text-sm"
                          placeholder="e.g. Negative, Non-reactive"
                        />
                      </div>
                    )}

                    <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded text-xs text-slate-500">
                      Standard Reference Range: <strong>{selectedTest.referenceRange || "Clinical evaluation"}</strong>
                    </div>

                    <div>
                      <label className="hms-label text-xs">Notes / Observations</label>
                      <textarea
                        value={resultNotes}
                        onChange={(e) => setResultNotes(e.target.value)}
                        className="hms-input text-sm"
                        placeholder="Optional technologist observations"
                        rows={2}
                      />
                    </div>

                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowResultModal(false)}
                        className="btn btn-sm btn-secondary"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={actionLoading}
                        className="btn btn-sm btn-primary"
                      >
                        Submit Result
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
