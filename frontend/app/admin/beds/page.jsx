"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import SilentLogin from "@/app/components/SilentLogin";
import { apiFetch } from "@/services/api";
import { getPatients, getDoctors } from "@/services/adminService";
import {
  getAllBeds, getAvailableBeds, createBed, updateBed, deleteBed,
  markBedCleaning, markBedReady, markBedMaintenance, getBedOccupancyHistory,
  getAllWards, createWard,
  getActiveAdmissions, admitPatient, transferBed, prepareDischarge, dischargePatient,
  getHistoricalOccupancy
} from "@/services/bedService";
import {
  FaBed, FaUserCheck, FaSignOutAlt, FaPlus, FaTools,
  FaCheckCircle, FaExclamationCircle, FaUser, FaUserMd,
  FaInfoCircle, FaCalendarAlt, FaDollarSign, FaHotel,
  FaExchangeAlt, FaBroom, FaHistory, FaChartLine, FaShieldAlt
} from "react-icons/fa";

export default function BedManagementPage() {
  const [activeTab, setActiveTab] = useState("beds"); // beds, admissions, wards, analytics
  const [beds, setBeds] = useState([]);
  const [availableBedsList, setAvailableBedsList] = useState([]);
  const [admissions, setAdmissions] = useState([]);
  const [wards, setWards] = useState([]);
  const [patients, setPatients] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Filters
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [wardFilter, setWardFilter] = useState("ALL");

  // Modals
  const [showAddBedModal, setShowAddBedModal] = useState(false);
  const [showAdmitModal, setShowAdmitModal] = useState(false);
  const [selectedBedForAdmission, setSelectedBedForAdmission] = useState(null);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [selectedAdmissionForTransfer, setSelectedAdmissionForTransfer] = useState(null);
  const [showHistoryModal, setShowHistoryModal] = useState(false);
  const [historyEvents, setHistoryEvents] = useState([]);
  const [historyTitle, setHistoryTitle] = useState("");
  const [showAddWardModal, setShowAddWardModal] = useState(false);

  // Forms
  const [newBedForm, setNewBedForm] = useState({
    bedNumber: "",
    type: "GENERAL",
    pricePerDay: 1500,
    ward: "General Ward",
    wardId: "",
    locationCode: "WING-A"
  });

  const [newWardForm, setNewWardForm] = useState({
    wardCode: "",
    wardName: "",
    wardType: "GENERAL",
    description: ""
  });

  const [admissionForm, setAdmissionForm] = useState({
    patientId: "",
    doctorId: "",
    admissionSource: "ELECTIVE",
    reasonCode: "OBSERVATION",
    notes: ""
  });

  const [transferForm, setTransferForm] = useState({
    destinationBedId: "",
    reasonCode: "CLINICAL_STEPDOWN",
    notes: ""
  });

  // Analytics State
  const [analyticsData, setAnalyticsData] = useState(null);
  const [analyticsPreset, setAnalyticsPreset] = useState("7D");

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (activeTab === "analytics") {
      loadAnalytics(analyticsPreset);
    }
  }, [activeTab, analyticsPreset]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError("");
      const [bedsData, availData, admissionsData, wardsData, patientsData, doctorsData] = await Promise.all([
        getAllBeds().catch(() => []),
        getAvailableBeds().catch(() => []),
        getActiveAdmissions().catch(() => []),
        getAllWards().catch(() => []),
        getPatients().catch(() => []),
        getDoctors().catch(() => [])
      ]);
      setBeds(bedsData || []);
      setAvailableBedsList(availData || []);
      setAdmissions(admissionsData || []);
      setWards(wardsData || []);
      setPatients(patientsData || []);
      setDoctors(doctorsData || []);
    } catch (err) {
      setError(err.message || "Failed to load inpatient management data.");
    } finally {
      setLoading(false);
    }
  };

  const loadAnalytics = async (preset) => {
    try {
      const data = await getHistoricalOccupancy({ preset });
      setAnalyticsData(data);
    } catch (err) {
      console.error("Failed to load occupancy analytics", err);
    }
  };

  // Handlers
  const handleAddBed = async (e) => {
    e.preventDefault();
    if (!newBedForm.bedNumber) {
      setError("Bed number is mandatory.");
      return;
    }
    try {
      setSubmitting(true);
      setError("");
      await createBed({
        ...newBedForm,
        pricePerDay: Number(newBedForm.pricePerDay || 0),
        dailyRate: Number(newBedForm.pricePerDay || 0)
      });
      setSuccess(`Bed ${newBedForm.bedNumber} added successfully.`);
      setShowAddBedModal(false);
      setNewBedForm({ bedNumber: "", type: "GENERAL", pricePerDay: 1500, ward: "General Ward", wardId: "", locationCode: "WING-A" });
      loadData();
    } catch (err) {
      setError(err.message || "Failed to add bed.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAddWard = async (e) => {
    e.preventDefault();
    if (!newWardForm.wardCode || !newWardForm.wardName) {
      setError("Ward code and ward name are mandatory.");
      return;
    }
    try {
      setSubmitting(true);
      setError("");
      await createWard(newWardForm);
      setSuccess(`Ward ${newWardForm.wardCode} created successfully.`);
      setShowAddWardModal(false);
      setNewWardForm({ wardCode: "", wardName: "", wardType: "GENERAL", description: "" });
      loadData();
    } catch (err) {
      setError(err.message || "Failed to create ward.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAdmit = async (e) => {
    e.preventDefault();
    if (!admissionForm.patientId) {
      setError("Please select a patient.");
      return;
    }
    try {
      setSubmitting(true);
      setError("");
      await admitPatient({
        patientId: admissionForm.patientId,
        doctorId: admissionForm.doctorId || undefined,
        bedId: selectedBedForAdmission.id,
        admissionSource: admissionForm.admissionSource,
        reasonCode: admissionForm.reasonCode,
        notes: admissionForm.notes,
        idempotencyKey: `ADM_${Date.now()}_${Math.random()}`
      });
      setSuccess(`Patient admitted to Bed ${selectedBedForAdmission.bedNumber} successfully.`);
      setShowAdmitModal(false);
      setAdmissionForm({ patientId: "", doctorId: "", admissionSource: "ELECTIVE", reasonCode: "OBSERVATION", notes: "" });
      loadData();
    } catch (err) {
      const isConflict = err.status === 409 || (err.message && err.message.toLowerCase().includes("allocated"));
      setError(isConflict ? "This bed has already been allocated or patient has an active admission." : (err.message || "Admission failed."));
      setShowAdmitModal(false);
      loadData();
    } finally {
      setSubmitting(false);
    }
  };

  const handleTransfer = async (e) => {
    e.preventDefault();
    if (!transferForm.destinationBedId) {
      setError("Please select a destination bed.");
      return;
    }
    try {
      setSubmitting(true);
      setError("");
      await transferBed(selectedAdmissionForTransfer.id, {
        destinationBedId: transferForm.destinationBedId,
        reasonCode: transferForm.reasonCode,
        notes: transferForm.notes,
        idempotencyKey: `TRF_${Date.now()}_${Math.random()}`
      });
      setSuccess("Patient transferred successfully. Source bed marked for cleaning turnover.");
      setShowTransferModal(false);
      setTransferForm({ destinationBedId: "", reasonCode: "CLINICAL_STEPDOWN", notes: "" });
      loadData();
    } catch (err) {
      setError(err.message || "Transfer failed.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDischargeReady = async (admissionId) => {
    try {
      setError("");
      await prepareDischarge(admissionId, { notes: "Clinical clearance certified by attending staff." });
      setSuccess("Clinical clearance marked. Patient is now ready for administrative discharge.");
      loadData();
    } catch (err) {
      setError(err.message || "Failed to mark discharge ready.");
    }
  };

  const handleDischarge = async (admissionId, bedNumber) => {
    if (!confirm(`Are you sure you want to discharge the patient from Bed ${bedNumber || ""}? Bed will be placed in turnover cleaning.`)) {
      return;
    }
    try {
      setError("");
      await dischargePatient(admissionId, { notes: "Discharged from inpatient care." });
      setSuccess(`Discharged successfully. Bed placed into cleaning turnover.`);
      loadData();
    } catch (err) {
      setError(err.message || "Failed to discharge patient.");
    }
  };

  const handleMarkReady = async (bedId) => {
    try {
      setError("");
      await markBedReady(bedId);
      setSuccess("Bed cleaning turnover completed. Bed is now AVAILABLE for new admissions.");
      loadData();
    } catch (err) {
      setError(err.message || "Failed to mark bed ready.");
    }
  };

  const handleMarkCleaning = async (bedId) => {
    try {
      setError("");
      await markBedCleaning(bedId);
      setSuccess("Bed marked for cleaning turnover.");
      loadData();
    } catch (err) {
      setError(err.message || "Failed to mark bed for cleaning.");
    }
  };

  const handleToggleMaintenance = async (bed) => {
    if (bed.status === "MAINTENANCE") {
      handleMarkReady(bed.id);
    } else {
      try {
        setError("");
        await markBedMaintenance(bed.id);
        setSuccess(`Bed ${bed.bedNumber} is now under maintenance.`);
        loadData();
      } catch (err) {
        setError(err.message || "Failed to update bed maintenance status.");
      }
    }
  };

  const handleViewBedHistory = async (bed) => {
    try {
      setError("");
      const events = await getBedOccupancyHistory(bed.id);
      setHistoryEvents(events || []);
      setHistoryTitle(`Occupancy Event Timeline: Bed ${bed.bedNumber} (${bed.ward || "General"})`);
      setShowHistoryModal(true);
    } catch (err) {
      setError(err.message || "Failed to load occupancy history.");
    }
  };

  // Filtered beds
  const filteredBeds = beds.filter(bed => {
    const matchesSearch = !searchTerm ||
      (bed.bedNumber && bed.bedNumber.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (bed.ward && bed.ward.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesStatus = statusFilter === "ALL" || bed.status === statusFilter;
    const matchesType = typeFilter === "ALL" || bed.type === typeFilter;
    const matchesWard = wardFilter === "ALL" || bed.ward === wardFilter || bed.wardId === wardFilter;
    return matchesSearch && matchesStatus && matchesType && matchesWard;
  });

  // Bed stats
  const totalBedsCount = beds.length;
  const availableBedsCount = beds.filter(b => b.status === "AVAILABLE").length;
  const occupiedBedsCount = beds.filter(b => b.status === "OCCUPIED").length;
  const cleaningBedsCount = beds.filter(b => b.status === "CLEANING").length;
  const maintenanceBedsCount = beds.filter(b => b.status === "MAINTENANCE").length;

  return (
    <ProtectedRoute allowedRoles={["ADMIN", "BED_MANAGER", "DOCTOR"]}>
      <div className="flex h-screen bg-gray-50 dark:bg-gray-900 font-sans">
        <SilentLogin />
        <Sidebar />
        <div className="flex-1 flex flex-col overflow-hidden">
          <Topbar />
          <main className="flex-1 overflow-y-auto p-6">

            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-6">
              <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
                  <FaBed className="text-blue-600" /> Inpatient & Bed Management Lifecycle
                </h1>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  Manage hospital wards, atomic admissions, patient bed transfers, safe discharges, and turnover.
                </p>
              </div>
              <div className="mt-4 md:mt-0 flex flex-wrap gap-2">
                <button
                  onClick={() => setShowAddWardModal(true)}
                  className="px-3 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg text-sm font-medium flex items-center gap-1.5 shadow-sm transition"
                >
                  <FaHotel /> Add Ward
                </button>
                <button
                  onClick={() => setShowAddBedModal(true)}
                  className="px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium flex items-center gap-1.5 shadow-sm transition"
                >
                  <FaPlus /> Add Bed
                </button>
              </div>
            </div>

            {/* Alerts */}
            {error && (
              <div className="mb-4 p-4 bg-red-50 dark:bg-red-900/30 border-l-4 border-red-500 text-red-700 dark:text-red-300 flex items-center justify-between rounded shadow-sm">
                <div className="flex items-center gap-2">
                  <FaExclamationCircle className="text-lg" />
                  <span>{error}</span>
                </div>
                <button onClick={() => setError("")} className="text-sm font-bold">&times;</button>
              </div>
            )}
            {success && (
              <div className="mb-4 p-4 bg-green-50 dark:bg-green-900/30 border-l-4 border-green-500 text-green-700 dark:text-green-300 flex items-center justify-between rounded shadow-sm">
                <div className="flex items-center gap-2">
                  <FaCheckCircle className="text-lg" />
                  <span>{success}</span>
                </div>
                <button onClick={() => setSuccess("")} className="text-sm font-bold">&times;</button>
              </div>
            )}

            {/* KPI Cards */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 mb-6">
              <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm">
                <span className="text-xs text-gray-500 dark:text-gray-400 font-medium">Total Beds</span>
                <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{totalBedsCount}</p>
              </div>
              <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm border-l-4 border-l-green-500">
                <span className="text-xs text-green-600 dark:text-green-400 font-medium">Available Beds</span>
                <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{availableBedsCount}</p>
              </div>
              <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm border-l-4 border-l-red-500">
                <span className="text-xs text-red-600 dark:text-red-400 font-medium">Occupied Beds</span>
                <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{occupiedBedsCount}</p>
              </div>
              <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm border-l-4 border-l-amber-500">
                <span className="text-xs text-amber-600 dark:text-amber-400 font-medium">Cleaning / Turnover</span>
                <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{cleaningBedsCount}</p>
              </div>
              <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm border-l-4 border-l-gray-500">
                <span className="text-xs text-gray-600 dark:text-gray-400 font-medium">Maintenance</span>
                <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{maintenanceBedsCount}</p>
              </div>
            </div>

            {/* Navigation Tabs */}
            <div className="flex border-b border-gray-200 dark:border-gray-700 mb-6 space-x-4">
              <button
                onClick={() => setActiveTab("beds")}
                className={`pb-2 px-3 text-sm font-semibold border-b-2 transition ${
                  activeTab === "beds"
                    ? "border-blue-600 text-blue-600 dark:text-blue-400"
                    : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                Bed Matrix ({filteredBeds.length})
              </button>
              <button
                onClick={() => setActiveTab("admissions")}
                className={`pb-2 px-3 text-sm font-semibold border-b-2 transition ${
                  activeTab === "admissions"
                    ? "border-blue-600 text-blue-600 dark:text-blue-400"
                    : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                Active Admissions ({admissions.length})
              </button>
              <button
                onClick={() => setActiveTab("wards")}
                className={`pb-2 px-3 text-sm font-semibold border-b-2 transition ${
                  activeTab === "wards"
                    ? "border-blue-600 text-blue-600 dark:text-blue-400"
                    : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                Wards ({wards.length})
              </button>
              <button
                onClick={() => setActiveTab("analytics")}
                className={`pb-2 px-3 text-sm font-semibold border-b-2 transition flex items-center gap-1.5 ${
                  activeTab === "analytics"
                    ? "border-blue-600 text-blue-600 dark:text-blue-400"
                    : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                <FaChartLine /> Historical Occupancy
              </button>
            </div>

            {/* TAB 1: BEDS MATRIX */}
            {activeTab === "beds" && (
              <>
                {/* Search & Filters */}
                <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm mb-6 flex flex-wrap gap-4 items-center">
                  <div className="flex-1 min-w-[200px]">
                    <input
                      type="text"
                      placeholder="Search bed number or ward..."
                      value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                    />
                  </div>
                  <div>
                    <select
                      value={statusFilter}
                      onChange={(e) => setStatusFilter(e.target.value)}
                      className="px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="AVAILABLE">Available</option>
                      <option value="OCCUPIED">Occupied</option>
                      <option value="CLEANING">Cleaning / Turnover</option>
                      <option value="MAINTENANCE">Maintenance</option>
                    </select>
                  </div>
                  <div>
                    <select
                      value={typeFilter}
                      onChange={(e) => setTypeFilter(e.target.value)}
                      className="px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                    >
                      <option value="ALL">All Bed Types</option>
                      <option value="GENERAL">General</option>
                      <option value="ICU">ICU</option>
                      <option value="SEMI_PRIVATE">Semi-Private</option>
                      <option value="PRIVATE">Private</option>
                    </select>
                  </div>
                </div>

                {/* Beds Table */}
                <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden">
                  <table className="w-full text-left border-collapse text-sm">
                    <thead>
                      <tr className="bg-gray-50 dark:bg-gray-900/50 border-b border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 font-semibold">
                        <th className="py-3 px-4">Bed Number</th>
                        <th className="py-3 px-4">Type</th>
                        <th className="py-3 px-4">Ward / Location</th>
                        <th className="py-3 px-4">Daily Rate</th>
                        <th className="py-3 px-4">Status</th>
                        <th className="py-3 px-4">Active Inpatient</th>
                        <th className="py-3 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                      {filteredBeds.length === 0 ? (
                        <tr>
                          <td colSpan="7" className="py-8 text-center text-gray-500">
                            No beds found matching your filter criteria.
                          </td>
                        </tr>
                      ) : (
                        filteredBeds.map(bed => {
                          const activeAdmission = admissions.find(a => (a.currentBedId === bed.id || a.bedId === bed.id));
                          return (
                            <tr key={bed.id} className="hover:bg-gray-50 dark:hover:bg-gray-750 transition">
                              <td className="py-3 px-4 font-bold text-gray-900 dark:text-white">
                                {bed.bedNumber}
                              </td>
                              <td className="py-3 px-4">
                                <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">
                                  {bed.type}
                                </span>
                              </td>
                              <td className="py-3 px-4 text-gray-600 dark:text-gray-300">
                                {bed.ward || "General"}
                                {bed.locationCode && <span className="text-xs text-gray-400 ml-1">({bed.locationCode})</span>}
                              </td>
                              <td className="py-3 px-4 font-semibold text-gray-700 dark:text-gray-300">
                                LKR {(bed.dailyRate || bed.pricePerDay || 0).toLocaleString()}
                              </td>
                              <td className="py-3 px-4">
                                {bed.status === "AVAILABLE" && (
                                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300">
                                    AVAILABLE
                                  </span>
                                )}
                                {bed.status === "OCCUPIED" && (
                                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300">
                                    OCCUPIED
                                  </span>
                                )}
                                {bed.status === "CLEANING" && (
                                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
                                    TURNOVER / CLEANING
                                  </span>
                                )}
                                {bed.status === "MAINTENANCE" && (
                                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-200 text-gray-800 dark:bg-gray-700 dark:text-gray-300">
                                    MAINTENANCE
                                  </span>
                                )}
                              </td>
                              <td className="py-3 px-4 text-gray-600 dark:text-gray-300">
                                {activeAdmission ? (
                                  <div>
                                    <p className="font-semibold text-gray-900 dark:text-white">{activeAdmission.patientName || "Patient"}</p>
                                    <p className="text-xs text-gray-400">{activeAdmission.admissionNumber}</p>
                                  </div>
                                ) : (
                                  <span className="text-gray-400 italic">None</span>
                                )}
                              </td>
                              <td className="py-3 px-4 text-right space-x-1.5">
                                {bed.status === "AVAILABLE" && (
                                  <button
                                    onClick={() => { setSelectedBedForAdmission(bed); setShowAdmitModal(true); }}
                                    className="px-2.5 py-1 bg-green-600 hover:bg-green-700 text-white rounded text-xs font-medium transition"
                                    title="Admit Patient"
                                  >
                                    Admit
                                  </button>
                                )}
                                {bed.status === "OCCUPIED" && activeAdmission && (
                                  <>
                                    <button
                                      onClick={() => { setSelectedAdmissionForTransfer(activeAdmission); setShowTransferModal(true); }}
                                      className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium transition"
                                      title="Transfer Bed"
                                    >
                                      Transfer
                                    </button>
                                    <button
                                      onClick={() => handleDischarge(activeAdmission.id, bed.bedNumber)}
                                      className="px-2 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-medium transition"
                                      title="Discharge"
                                    >
                                      Discharge
                                    </button>
                                  </>
                                )}
                                {bed.status === "CLEANING" && (
                                  <button
                                    onClick={() => handleMarkReady(bed.id)}
                                    className="px-2.5 py-1 bg-amber-600 hover:bg-amber-700 text-white rounded text-xs font-medium transition"
                                    title="Mark Cleaned and Ready"
                                  >
                                    <FaBroom className="inline mr-1" /> Mark Ready
                                  </button>
                                )}
                                <button
                                  onClick={() => handleViewBedHistory(bed)}
                                  className="px-2 py-1 bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded text-xs transition"
                                  title="Occupancy History"
                                >
                                  <FaHistory />
                                </button>
                                <button
                                  onClick={() => handleToggleMaintenance(bed)}
                                  className="px-2 py-1 bg-gray-100 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded text-xs transition"
                                  title="Toggle Maintenance"
                                >
                                  <FaTools />
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}

            {/* TAB 2: ACTIVE ADMISSIONS */}
            {activeTab === "admissions" && (
              <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden">
                <table className="w-full text-left border-collapse text-sm">
                  <thead>
                    <tr className="bg-gray-50 dark:bg-gray-900/50 border-b border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 font-semibold">
                      <th className="py-3 px-4">Admission #</th>
                      <th className="py-3 px-4">Patient</th>
                      <th className="py-3 px-4">Doctor</th>
                      <th className="py-3 px-4">Bed & Ward</th>
                      <th className="py-3 px-4">Admitted At</th>
                      <th className="py-3 px-4">Status</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                    {admissions.length === 0 ? (
                      <tr>
                        <td colSpan="7" className="py-8 text-center text-gray-500">No active inpatient admissions currently.</td>
                      </tr>
                    ) : (
                      admissions.map(adm => (
                        <tr key={adm.id} className="hover:bg-gray-50 dark:hover:bg-gray-750 transition">
                          <td className="py-3 px-4 font-mono font-bold text-blue-600 dark:text-blue-400">{adm.admissionNumber}</td>
                          <td className="py-3 px-4 font-semibold text-gray-900 dark:text-white">{adm.patientName || adm.patientId}</td>
                          <td className="py-3 px-4 text-gray-600 dark:text-gray-300">{adm.doctorName || "Attending Doctor"}</td>
                          <td className="py-3 px-4">
                            <span className="font-semibold text-gray-900 dark:text-white">Bed {adm.bedNumber || adm.bedId}</span>
                            <span className="text-xs text-gray-500 block">{adm.ward || "General Ward"}</span>
                          </td>
                          <td className="py-3 px-4 text-gray-600 dark:text-gray-400">
                            {adm.admittedAt ? new Date(adm.admittedAt).toLocaleString() : adm.admissionDate}
                          </td>
                          <td className="py-3 px-4">
                            <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
                              {adm.status}
                            </span>
                          </td>
                          <td className="py-3 px-4 text-right space-x-2">
                            {adm.status !== "DISCHARGE_READY" && (
                              <button
                                onClick={() => handleDischargeReady(adm.id)}
                                className="px-2 py-1 bg-purple-600 hover:bg-purple-700 text-white rounded text-xs font-medium transition"
                              >
                                Discharge Ready
                              </button>
                            )}
                            <button
                              onClick={() => { setSelectedAdmissionForTransfer(adm); setShowTransferModal(true); }}
                              className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium transition"
                            >
                              Transfer
                            </button>
                            <button
                              onClick={() => handleDischarge(adm.id, adm.bedNumber)}
                              className="px-2 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-medium transition"
                            >
                              Discharge
                            </button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}

            {/* TAB 3: WARDS MANAGEMENT */}
            {activeTab === "wards" && (
              <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden">
                <table className="w-full text-left border-collapse text-sm">
                  <thead>
                    <tr className="bg-gray-50 dark:bg-gray-900/50 border-b border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 font-semibold">
                      <th className="py-3 px-4">Ward Code</th>
                      <th className="py-3 px-4">Ward Name</th>
                      <th className="py-3 px-4">Type</th>
                      <th className="py-3 px-4">Description</th>
                      <th className="py-3 px-4">Active</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                    {wards.length === 0 ? (
                      <tr>
                        <td colSpan="5" className="py-8 text-center text-gray-500">No hospital wards registered yet.</td>
                      </tr>
                    ) : (
                      wards.map(ward => (
                        <tr key={ward.id} className="hover:bg-gray-50 dark:hover:bg-gray-750 transition">
                          <td className="py-3 px-4 font-mono font-bold text-gray-900 dark:text-white">{ward.wardCode}</td>
                          <td className="py-3 px-4 font-semibold text-gray-800 dark:text-gray-200">{ward.wardName}</td>
                          <td className="py-3 px-4">
                            <span className="px-2 py-0.5 rounded text-xs font-medium bg-purple-50 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">
                              {ward.wardType}
                            </span>
                          </td>
                          <td className="py-3 px-4 text-gray-500">{ward.description || "—"}</td>
                          <td className="py-3 px-4">
                            <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${ward.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>
                              {ward.active ? 'ACTIVE' : 'INACTIVE'}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}

            {/* TAB 4: HISTORICAL OCCUPANCY ANALYTICS */}
            {activeTab === "analytics" && (
              <div className="space-y-6">
                <div className="flex gap-2">
                  {["TODAY", "7D", "30D"].map(p => (
                    <button
                      key={p}
                      onClick={() => setAnalyticsPreset(p)}
                      className={`px-3 py-1.5 rounded-lg text-sm font-semibold transition ${
                        analyticsPreset === p ? "bg-blue-600 text-white" : "bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 border border-gray-200"
                      }`}
                    >
                      {p === "TODAY" ? "Today" : p === "7D" ? "Last 7 Days" : "Last 30 Days"}
                    </button>
                  ))}
                </div>

                {analyticsData && (
                  <>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm">
                        <span className="text-xs text-gray-500 font-medium">Occupancy Rate</span>
                        <p className="text-2xl font-bold text-blue-600 mt-1">{analyticsData.occupancyRatePercentage}%</p>
                      </div>
                      <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm">
                        <span className="text-xs text-gray-500 font-medium">Occupied Bed-Hours</span>
                        <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">{analyticsData.totalOccupiedBedHours} hrs</p>
                      </div>
                      <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm">
                        <span className="text-xs text-gray-500 font-medium">Average Stay Duration</span>
                        <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">
                          {analyticsData.averageLengthOfStayHours > 0 ? (analyticsData.averageLengthOfStayHours / 24).toFixed(1) + " days" : "—"}
                        </p>
                      </div>
                      <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm">
                        <span className="text-xs text-gray-500 font-medium">Admissions / Discharges</span>
                        <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1">
                          {analyticsData.admissionsCount} / {analyticsData.dischargesCount}
                        </p>
                      </div>
                    </div>

                    <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg text-xs text-blue-800 dark:text-blue-300 flex items-center justify-between">
                      <span className="font-semibold">Completeness: {analyticsData.dataCompletenessStatus}</span>
                      <span>{analyticsData.completenessNotice}</span>
                    </div>

                    {/* Daily Breakdown Table */}
                    {analyticsData.dailyTimeline && analyticsData.dailyTimeline.length > 0 && (
                      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden">
                        <div className="p-4 border-b border-gray-200 dark:border-gray-700 font-bold text-gray-900 dark:text-white">
                          Daily Historical Timeline
                        </div>
                        <table className="w-full text-left text-sm">
                          <thead>
                            <tr className="bg-gray-50 dark:bg-gray-900/50 text-gray-500 font-semibold border-b border-gray-200 dark:border-gray-700">
                              <th className="p-3">Date</th>
                              <th className="p-3">Occupied Bed-Hours</th>
                              <th className="p-3">Available Bed-Hours</th>
                              <th className="p-3">Occupancy %</th>
                              <th className="p-3">Admissions</th>
                              <th className="p-3">Discharges</th>
                              <th className="p-3">Transfers</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                            {analyticsData.dailyTimeline.map(d => (
                              <tr key={d.date} className="hover:bg-gray-50 dark:hover:bg-gray-750">
                                <td className="p-3 font-semibold text-gray-900 dark:text-white">{d.date}</td>
                                <td className="p-3">{d.occupiedBedHours} hrs</td>
                                <td className="p-3">{d.availableBedHours} hrs</td>
                                <td className="p-3 font-bold text-blue-600">{d.occupancyRatePercentage}%</td>
                                <td className="p-3">{d.admissions}</td>
                                <td className="p-3">{d.discharges}</td>
                                <td className="p-3">{d.transfers}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </>
                )}
              </div>
            )}

            {/* MODAL 1: ADD BED */}
            {showAddBedModal && (
              <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                <div className="bg-white dark:bg-gray-800 rounded-xl p-6 max-w-md w-full shadow-xl">
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Add Hospital Bed</h3>
                  <form onSubmit={handleAddBed} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Bed Number</label>
                      <input
                        type="text"
                        placeholder="e.g. ICU-01, GEN-102"
                        value={newBedForm.bedNumber}
                        onChange={(e) => setNewBedForm({ ...newBedForm, bedNumber: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Bed Type</label>
                      <select
                        value={newBedForm.type}
                        onChange={(e) => setNewBedForm({ ...newBedForm, type: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                      >
                        <option value="GENERAL">General</option>
                        <option value="ICU">ICU</option>
                        <option value="SEMI_PRIVATE">Semi-Private</option>
                        <option value="PRIVATE">Private</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Ward Name</label>
                      <input
                        type="text"
                        value={newBedForm.ward}
                        onChange={(e) => setNewBedForm({ ...newBedForm, ward: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Daily Billing Rate (LKR)</label>
                      <input
                        type="number"
                        value={newBedForm.pricePerDay}
                        onChange={(e) => setNewBedForm({ ...newBedForm, pricePerDay: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      />
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowAddBedModal(false)}
                        className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
                      >
                        {submitting ? "Adding..." : "Add Bed"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* MODAL 2: ADMIT PATIENT */}
            {showAdmitModal && selectedBedForAdmission && (
              <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                <div className="bg-white dark:bg-gray-800 rounded-xl p-6 max-w-md w-full shadow-xl">
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">
                    Inpatient Admission: Bed {selectedBedForAdmission.bedNumber}
                  </h3>
                  <p className="text-xs text-gray-500 mb-4">Ward: {selectedBedForAdmission.ward || "General"} | Type: {selectedBedForAdmission.type}</p>
                  <form onSubmit={handleAdmit} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Select Patient *</label>
                      <select
                        value={admissionForm.patientId}
                        onChange={(e) => setAdmissionForm({ ...admissionForm, patientId: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      >
                        <option value="">-- Choose Patient --</option>
                        {patients.map(p => (
                          <option key={p.id} value={p.id}>{p.fullName} ({p.email || p.phone || p.id})</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Attending Doctor (Optional)</label>
                      <select
                        value={admissionForm.doctorId}
                        onChange={(e) => setAdmissionForm({ ...admissionForm, doctorId: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                      >
                        <option value="">-- Assigned by Department --</option>
                        {doctors.map(d => (
                          <option key={d.id} value={d.id}>{d.fullName} ({d.specialization})</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Admission Source</label>
                      <select
                        value={admissionForm.admissionSource}
                        onChange={(e) => setAdmissionForm({ ...admissionForm, admissionSource: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                      >
                        <option value="ELECTIVE">Elective</option>
                        <option value="EMERGENCY">Emergency</option>
                        <option value="CLINICAL_REFERRAL">Clinical Referral</option>
                        <option value="TRANSFER">Inter-Hospital Transfer</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Reason Code / Clinical Indication</label>
                      <input
                        type="text"
                        value={admissionForm.reasonCode}
                        onChange={(e) => setAdmissionForm({ ...admissionForm, reasonCode: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        placeholder="e.g. POST_OP_RECOVERY, ICU_VENTILATION"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Admission Notes</label>
                      <textarea
                        rows="2"
                        value={admissionForm.notes}
                        onChange={(e) => setAdmissionForm({ ...admissionForm, notes: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        placeholder="Administrative notes, room special requests..."
                      ></textarea>
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowAdmitModal(false)}
                        className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
                      >
                        {submitting ? "Admitting..." : "Confirm Admission"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* MODAL 3: TRANSFER BED */}
            {showTransferModal && selectedAdmissionForTransfer && (
              <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                <div className="bg-white dark:bg-gray-800 rounded-xl p-6 max-w-md w-full shadow-xl">
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">
                    Transfer Patient: {selectedAdmissionForTransfer.patientName || "Patient"}
                  </h3>
                  <p className="text-xs text-gray-500 mb-4">
                    Current Bed: {selectedAdmissionForTransfer.bedNumber} ({selectedAdmissionForTransfer.ward || "General"})
                  </p>
                  <form onSubmit={handleTransfer} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">
                        Select Destination Bed (Available Only) *
                      </label>
                      <select
                        value={transferForm.destinationBedId}
                        onChange={(e) => setTransferForm({ ...transferForm, destinationBedId: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      >
                        <option value="">-- Choose Available Bed --</option>
                        {availableBedsList.map(b => (
                          <option key={b.id} value={b.id}>
                            Bed {b.bedNumber} ({b.ward || "General"}) - {b.type} (LKR {b.dailyRate || b.pricePerDay}/day)
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Transfer Reason Code *</label>
                      <select
                        value={transferForm.reasonCode}
                        onChange={(e) => setTransferForm({ ...transferForm, reasonCode: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      >
                        <option value="CLINICAL_STEPDOWN">Clinical Stepdown (ICU to General)</option>
                        <option value="CLINICAL_ESCALATION">Clinical Escalation (General to ICU)</option>
                        <option value="WARD_TRANSFER">Ward / Wing Reallocation</option>
                        <option value="PATIENT_REQUEST">Patient / Family Request</option>
                        <option value="ISOLATION_REQUIRED">Infection Control / Isolation</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Transfer Notes</label>
                      <textarea
                        rows="2"
                        value={transferForm.notes}
                        onChange={(e) => setTransferForm({ ...transferForm, notes: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        placeholder="Staff handover notes, equipment required..."
                      ></textarea>
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowTransferModal(false)}
                        className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
                      >
                        {submitting ? "Transferring..." : "Confirm Transfer"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* MODAL 4: OCCUPANCY EVENT TIMELINE */}
            {showHistoryModal && (
              <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                <div className="bg-white dark:bg-gray-800 rounded-xl p-6 max-w-2xl w-full shadow-xl max-h-[80vh] flex flex-col">
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-1 flex items-center gap-2">
                    <FaHistory className="text-blue-600" /> {historyTitle}
                  </h3>
                  <p className="text-xs text-gray-500 mb-4">Immutable append-only occupancy audit trail.</p>

                  <div className="flex-1 overflow-y-auto space-y-3 pr-2">
                    {historyEvents.length === 0 ? (
                      <p className="text-center text-gray-500 py-6">No historical occupancy events recorded for this bed yet.</p>
                    ) : (
                      historyEvents.map((evt, idx) => (
                        <div key={evt.id || idx} className="p-3 bg-gray-50 dark:bg-gray-900/60 rounded-lg border border-gray-100 dark:border-gray-700 text-xs">
                          <div className="flex justify-between items-center mb-1">
                            <span className="font-bold text-blue-600 dark:text-blue-400">{evt.eventType}</span>
                            <span className="text-gray-400">{evt.occurredAt ? new Date(evt.occurredAt).toLocaleString() : ""}</span>
                          </div>
                          <div className="text-gray-600 dark:text-gray-300 space-y-0.5">
                            {evt.reasonCode && <p>Reason: <span className="font-semibold">{evt.reasonCode}</span></p>}
                            {evt.patientId && <p>Patient: {evt.patientId}</p>}
                            {evt.destinationBedId && <p>To Bed: {evt.destinationBedId}</p>}
                            {evt.sourceBedId && <p>From Bed: {evt.sourceBedId}</p>}
                          </div>
                        </div>
                      ))
                    )}
                  </div>

                  <div className="flex justify-end pt-4 border-t border-gray-200 dark:border-gray-700 mt-2">
                    <button
                      onClick={() => setShowHistoryModal(false)}
                      className="px-4 py-2 bg-gray-200 hover:bg-gray-300 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-800 dark:text-gray-200 rounded-lg text-sm font-medium"
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* MODAL 5: ADD WARD */}
            {showAddWardModal && (
              <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
                <div className="bg-white dark:bg-gray-800 rounded-xl p-6 max-w-md w-full shadow-xl">
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Add Hospital Ward</h3>
                  <form onSubmit={handleAddWard} className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Ward Code *</label>
                      <input
                        type="text"
                        placeholder="e.g. WARD-GEN-A, WARD-ICU-01"
                        value={newWardForm.wardCode}
                        onChange={(e) => setNewWardForm({ ...newWardForm, wardCode: e.target.value.toUpperCase() })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white uppercase"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Ward Name *</label>
                      <input
                        type="text"
                        placeholder="e.g. General Surgical Ward A"
                        value={newWardForm.wardName}
                        onChange={(e) => setNewWardForm({ ...newWardForm, wardName: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Ward Type</label>
                      <select
                        value={newWardForm.wardType}
                        onChange={(e) => setNewWardForm({ ...newWardForm, wardType: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                      >
                        <option value="GENERAL">General</option>
                        <option value="ICU">ICU</option>
                        <option value="SURGICAL">Surgical</option>
                        <option value="PEDIATRIC">Pediatric</option>
                        <option value="MATERNITY">Maternity</option>
                        <option value="EMERGENCY">Emergency</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 dark:text-gray-300 mb-1">Description</label>
                      <textarea
                        rows="2"
                        value={newWardForm.description}
                        onChange={(e) => setNewWardForm({ ...newWardForm, description: e.target.value })}
                        className="w-full px-3 py-2 border rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:border-gray-700 dark:text-white"
                      ></textarea>
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowAddWardModal(false)}
                        className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={submitting}
                        className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
                      >
                        {submitting ? "Saving..." : "Create Ward"}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

          </main>
        </div>
      </div>
    </ProtectedRoute>
  );
}
