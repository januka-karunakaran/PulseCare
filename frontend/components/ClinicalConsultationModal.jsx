"use client";

import { useEffect, useState, useCallback } from "react";
import {
  startOrResumeEncounter,
  saveSoapNoteDraft,
  signEncounter,
  amendEncounter,
  recordVitals,
  getPatientProfile,
} from "@/services/clinicalService";
import {
  FaTimes,
  FaHeartbeat,
  FaFileMedical,
  FaCheckCircle,
  FaExclamationTriangle,
  FaHistory,
  FaLock,
  FaSave,
  FaFlask,
} from "react-icons/fa";
import { getActiveLabTests, createLabOrder } from "@/services/labService";

export default function ClinicalConsultationModal({ appointment, patient, onClose, onUpdated }) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  const [encounter, setEncounter] = useState(null);
  const [profile, setProfile] = useState(null);

  // SOAP Fields
  const [chiefComplaint, setChiefComplaint] = useState("");
  const [subjective, setSubjective] = useState("");
  const [objective, setObjective] = useState("");
  const [assessment, setAssessment] = useState("");
  const [diagnosisText, setDiagnosisText] = useState("");
  const [diagnosisStatus, setDiagnosisStatus] = useState("PROVISIONAL");
  const [plan, setPlan] = useState("");
  const [testsRequested, setTestsRequested] = useState("");
  const [followUpInstructions, setFollowUpInstructions] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");

  // Vitals Fields
  const [systolicBp, setSystolicBp] = useState("");
  const [diastolicBp, setDiastolicBp] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [oxygenSaturation, setOxygenSaturation] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [painScore, setPainScore] = useState("");
  const [vitalsSavedMsg, setVitalsSavedMsg] = useState("");

  // Amendment state
  const [amendMode, setAmendMode] = useState(false);
  const [amendmentReason, setAmendmentReason] = useState("");
  const [confirmSignModal, setConfirmSignModal] = useState(false);

  // Laboratory ordering state
  const [catalogTests, setCatalogTests] = useState([]);
  const [selectedLabCodes, setSelectedLabCodes] = useState([]);
  const [labPriority, setLabPriority] = useState("ROUTINE");
  const [labOrderSuccess, setLabOrderSuccess] = useState("");
  const [orderingLab, setOrderingLab] = useState(false);

  const initEncounter = useCallback(async () => {
    if (!appointment?.id) return;
    try {
      setLoading(true);
      setError("");
      // 1. Start or resume encounter
      const enc = await startOrResumeEncounter({
        appointmentId: appointment.id,
        chiefComplaint: appointment.reason || "Routine Consultation",
      });
      setEncounter(enc);

      // Populate SOAP fields from current note
      if (enc.currentNote) {
        setChiefComplaint(enc.currentNote.chiefComplaint || enc.chiefComplaint || "");
        setSubjective(enc.currentNote.subjective || "");
        setObjective(enc.currentNote.objective || "");
        setAssessment(enc.currentNote.assessment || "");
        setDiagnosisText(enc.currentNote.diagnosisText || "");
        setDiagnosisStatus(enc.currentNote.diagnosisStatus || "PROVISIONAL");
        setPlan(enc.currentNote.plan || "");
        setTestsRequested(enc.currentNote.testsRequested || "");
        setFollowUpInstructions(enc.currentNote.followUpInstructions || "");
        setFollowUpDate(enc.currentNote.followUpDate || "");
      }

      // Populate latest vitals if available
      if (enc.observations && enc.observations.length > 0) {
        const latest = enc.observations[0];
        setSystolicBp(latest.systolicBp != null ? String(latest.systolicBp) : "");
        setDiastolicBp(latest.diastolicBp != null ? String(latest.diastolicBp) : "");
        setHeartRate(latest.heartRate != null ? String(latest.heartRate) : "");
        setTemperature(latest.temperature != null ? String(latest.temperature) : "");
        setOxygenSaturation(latest.oxygenSaturation != null ? String(latest.oxygenSaturation) : "");
        setRespiratoryRate(latest.respiratoryRate != null ? String(latest.respiratoryRate) : "");
        setPainScore(latest.painScore != null ? String(latest.painScore) : "");
      }

      // 2. Fetch patient clinical profile for allergies and history
      if (appointment.patientId) {
        const prof = await getPatientProfile(appointment.patientId);
        setProfile(prof);
      }

      // 3. Load active laboratory catalog
      try {
        const tests = await getActiveLabTests();
        setCatalogTests(tests || []);
      } catch {
        // Non-blocking
      }
    } catch (err) {
      setError(err.message || "Failed to load clinical encounter");
    } finally {
      setLoading(false);
    }
  }, [appointment]);

  useEffect(() => {
    initEncounter();
  }, [initEncounter]);

  const handleOrderLabTests = async (e) => {
    e.preventDefault();
    if (!selectedLabCodes.length || !appointment?.patientId) return;
    try {
      setOrderingLab(true);
      setError("");
      setLabOrderSuccess("");
      await createLabOrder({
        patientId: appointment.patientId,
        encounterId: encounter?.id,
        appointmentId: appointment.id,
        testCodes: selectedLabCodes,
        priority: labPriority,
        clinicalNotes: "Requested during consultation for: " + (chiefComplaint || "Encounter"),
      });
      setLabOrderSuccess(`Successfully submitted laboratory order for ${selectedLabCodes.length} test(s).`);
      setSelectedLabCodes([]);
    } catch (err) {
      setError(err.message || "Failed to submit laboratory order");
    } finally {
      setOrderingLab(false);
    }
  };

  const handleSaveVitals = async (e) => {
    e.preventDefault();
    if (!encounter?.id) return;
    try {
      setSaving(true);
      setError("");
      setVitalsSavedMsg("");
      await recordVitals(encounter.id, {
        systolicBp: systolicBp ? parseInt(systolicBp, 10) : null,
        diastolicBp: diastolicBp ? parseInt(diastolicBp, 10) : null,
        heartRate: heartRate ? parseInt(heartRate, 10) : null,
        temperature: temperature ? parseFloat(temperature) : null,
        oxygenSaturation: oxygenSaturation ? parseInt(oxygenSaturation, 10) : null,
        respiratoryRate: respiratoryRate ? parseInt(respiratoryRate, 10) : null,
        painScore: painScore ? parseInt(painScore, 10) : null,
      });
      setVitalsSavedMsg("Vitals saved successfully");
      setTimeout(() => setVitalsSavedMsg(""), 3000);
    } catch (err) {
      setError(err.message || "Invalid vitals values");
    } finally {
      setSaving(false);
    }
  };

  const handleSaveDraft = async () => {
    if (!encounter?.id) return;
    try {
      setSaving(true);
      setError("");
      setSuccessMsg("");
      const updated = await saveSoapNoteDraft(encounter.id, {
        chiefComplaint,
        subjective,
        objective,
        assessment,
        diagnosisText,
        diagnosisStatus,
        plan,
        testsRequested,
        followUpInstructions,
        followUpDate,
        expectedVersion: encounter.currentNote?.version,
      });
      setEncounter(updated);
      setSuccessMsg("Draft saved successfully.");
      setTimeout(() => setSuccessMsg(""), 3000);
      if (onUpdated) onUpdated();
    } catch (err) {
      setError(err.message || "Failed to save draft");
    } finally {
      setSaving(false);
    }
  };

  const handleSignFinalize = async () => {
    if (!encounter?.id) return;
    try {
      setSaving(true);
      setError("");
      setConfirmSignModal(false);
      const signed = await signEncounter(encounter.id, {
        expectedEncounterVersion: encounter.version,
        noteData: {
          chiefComplaint,
          subjective,
          objective,
          assessment,
          diagnosisText,
          diagnosisStatus,
          plan,
          testsRequested,
          followUpInstructions,
          followUpDate,
        },
      });
      setEncounter(signed);
      setSuccessMsg("Consultation signed and finalized successfully!");
      if (onUpdated) onUpdated();
    } catch (err) {
      setError(err.message || "Failed to sign encounter");
    } finally {
      setSaving(false);
    }
  };

  const handleAmend = async (e) => {
    e.preventDefault();
    if (!encounter?.id || !amendmentReason.trim()) return;
    try {
      setSaving(true);
      setError("");
      const amended = await amendEncounter(encounter.id, {
        amendmentReason,
        expectedEncounterVersion: encounter.version,
        noteData: {
          chiefComplaint,
          subjective,
          objective,
          assessment,
          diagnosisText,
          diagnosisStatus,
          plan,
          testsRequested,
          followUpInstructions,
          followUpDate,
        },
      });
      setEncounter(amended);
      setAmendMode(false);
      setAmendmentReason("");
      setSuccessMsg("Amendment successfully appended to clinical record.");
      if (onUpdated) onUpdated();
    } catch (err) {
      setError(err.message || "Failed to amend encounter");
    } finally {
      setSaving(false);
    }
  };

  const isFinalized = encounter?.status === "SIGNED" || encounter?.status === "AMENDED";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white dark:bg-slate-900 w-full max-w-5xl rounded-2xl shadow-2xl border border-slate-200 dark:border-slate-800 my-8 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 bg-slate-100 dark:bg-slate-800/80 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-600 text-white rounded-lg">
              <FaFileMedical className="text-lg" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                Clinical Encounter Chart
                {encounter && (
                  <span
                    className={`text-xs px-2.5 py-0.5 rounded-full font-bold ${
                      isFinalized
                        ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300"
                        : "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300"
                    }`}
                  >
                    {encounter.status}
                  </span>
                )}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Patient: <span className="font-semibold text-slate-700 dark:text-slate-200">{patient?.fullName || "Patient"}</span> | Token: #{appointment?.tokenNumber || "—"} | Date: {appointment?.appointmentDate}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg transition-colors"
          >
            <FaTimes />
          </button>
        </div>

        {/* Alerts */}
        {error && (
          <div className="mx-6 mt-4 p-3 bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-sm rounded-lg flex items-center gap-2">
            <FaExclamationTriangle className="shrink-0" />
            <span>{error}</span>
          </div>
        )}
        {successMsg && (
          <div className="mx-6 mt-4 p-3 bg-emerald-50 dark:bg-emerald-950/50 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300 text-sm rounded-lg flex items-center gap-2">
            <FaCheckCircle className="shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {loading ? (
            <div className="py-16 text-center text-slate-400">Loading patient chart and encounter...</div>
          ) : (
            <>
              {/* Patient Banner & Clinical Summary */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-slate-50 dark:bg-slate-800/40 p-4 rounded-xl border border-slate-200 dark:border-slate-800 text-xs">
                <div>
                  <span className="text-slate-400 font-medium block">Allergy Status</span>
                  {profile?.allergyStatus === "CONFIRMED_ALLERGIES" ? (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {profile.allergies?.map((a, i) => (
                        <span key={i} className="px-2 py-0.5 bg-rose-100 text-rose-700 font-bold rounded">
                          {a.allergen} ({a.severity})
                        </span>
                      ))}
                    </div>
                  ) : profile?.allergyStatus === "NO_KNOWN_ALLERGIES" ? (
                    <span className="font-semibold text-emerald-600 mt-1 block">No Known Drug Allergies (NKDA)</span>
                  ) : (
                    <span className="font-semibold text-amber-600 mt-1 block">Unknown / Unrecorded</span>
                  )}
                </div>
                <div>
                  <span className="text-slate-400 font-medium block">Chronic Conditions</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200 mt-1 block">
                    {profile?.chronicConditions?.length ? profile.chronicConditions.join(", ") : "None recorded"}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 font-medium block">Past Medical History</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200 mt-1 block">
                    {profile?.pastMedicalHistory?.length ? profile.pastMedicalHistory.join(", ") : "None recorded"}
                  </span>
                </div>
              </div>

              {/* Vital Signs Form */}
              <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold text-slate-800 dark:text-slate-200 flex items-center gap-2">
                    <FaHeartbeat className="text-rose-500" /> Vital Signs & Observations
                  </h3>
                  {vitalsSavedMsg && <span className="text-xs text-emerald-600 font-semibold">{vitalsSavedMsg}</span>}
                </div>
                <form onSubmit={handleSaveVitals} className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-7 gap-3 text-xs">
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">Sys BP (mmHg)</label>
                    <input
                      type="number"
                      placeholder="120"
                      disabled={isFinalized && !amendMode}
                      value={systolicBp}
                      onChange={(e) => setSystolicBp(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">Dia BP (mmHg)</label>
                    <input
                      type="number"
                      placeholder="80"
                      disabled={isFinalized && !amendMode}
                      value={diastolicBp}
                      onChange={(e) => setDiastolicBp(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">Heart Rate (bpm)</label>
                    <input
                      type="number"
                      placeholder="72"
                      disabled={isFinalized && !amendMode}
                      value={heartRate}
                      onChange={(e) => setHeartRate(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">Temp (°C)</label>
                    <input
                      type="number"
                      step="0.1"
                      placeholder="37.0"
                      disabled={isFinalized && !amendMode}
                      value={temperature}
                      onChange={(e) => setTemperature(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">SpO2 (%)</label>
                    <input
                      type="number"
                      placeholder="98"
                      disabled={isFinalized && !amendMode}
                      value={oxygenSaturation}
                      onChange={(e) => setOxygenSaturation(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div>
                    <label className="text-slate-500 font-medium block mb-1">Resp Rate (/min)</label>
                    <input
                      type="number"
                      placeholder="16"
                      disabled={isFinalized && !amendMode}
                      value={respiratoryRate}
                      onChange={(e) => setRespiratoryRate(e.target.value)}
                      className="w-full px-2.5 py-1.5 border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                    />
                  </div>
                  <div className="flex items-end">
                    {(!isFinalized || amendMode) && (
                      <button
                        type="submit"
                        disabled={saving}
                        className="w-full py-1.5 bg-slate-800 hover:bg-slate-900 text-white font-semibold rounded-lg dark:bg-slate-700 transition"
                      >
                        Save Vitals
                      </button>
                    )}
                  </div>
                </form>
              </div>

              {/* SOAP Documentation Editor */}
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b pb-2 dark:border-slate-800">
                  <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">
                    SOAP Clinical Consultation Notes
                  </h3>
                  {isFinalized && (
                    <span className="text-xs text-slate-500 flex items-center gap-1">
                      <FaLock className="text-slate-400" /> Signed by {encounter.signedByDoctorName || "Doctor"} at {new Date(encounter.signedAt).toLocaleString()}
                    </span>
                  )}
                </div>

                {/* S - Subjective */}
                <div className="space-y-2">
                  <label className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block">
                    S — Subjective (Symptoms & History)
                  </label>
                  <textarea
                    rows={2}
                    disabled={isFinalized && !amendMode}
                    value={subjective}
                    onChange={(e) => setSubjective(e.target.value)}
                    placeholder="Patient describes onset, duration, severity, and character of symptoms..."
                    className="w-full p-2.5 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                  />
                </div>

                {/* O - Objective */}
                <div className="space-y-2">
                  <label className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block">
                    O — Objective (Examination Findings)
                  </label>
                  <textarea
                    rows={2}
                    disabled={isFinalized && !amendMode}
                    value={objective}
                    onChange={(e) => setObjective(e.target.value)}
                    placeholder="Physical examination observations, heart sounds, chest auscultation, palpation..."
                    className="w-full p-2.5 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                  />
                </div>

                {/* A - Assessment */}
                <div className="space-y-2">
                  <label className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block">
                    A — Assessment & Diagnosis
                  </label>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="sm:col-span-2">
                      <input
                        type="text"
                        disabled={isFinalized && !amendMode}
                        placeholder="Clinician-entered Primary Diagnosis (e.g. Acute Bronchitis)"
                        value={diagnosisText}
                        onChange={(e) => setDiagnosisText(e.target.value)}
                        className="w-full p-2 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700 font-semibold"
                      />
                    </div>
                    <div>
                      <select
                        disabled={isFinalized && !amendMode}
                        value={diagnosisStatus}
                        onChange={(e) => setDiagnosisStatus(e.target.value)}
                        className="w-full p-2 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                      >
                        <option value="PROVISIONAL">Provisional</option>
                        <option value="CONFIRMED">Confirmed</option>
                        <option value="DIFFERENTIAL">Differential</option>
                      </select>
                    </div>
                  </div>
                  <textarea
                    rows={2}
                    disabled={isFinalized && !amendMode}
                    value={assessment}
                    onChange={(e) => setAssessment(e.target.value)}
                    placeholder="Clinical judgment, clinical assessment, differential considerations..."
                    className="w-full p-2.5 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                  />
                </div>

                {/* P - Plan */}
                <div className="space-y-2">
                  <label className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider block">
                    P — Plan (Treatment, Follow-up & Orders)
                  </label>
                  <textarea
                    rows={2}
                    disabled={isFinalized && !amendMode}
                    value={plan}
                    onChange={(e) => setPlan(e.target.value)}
                    placeholder="Clinician treatment plan, requested tests, therapy..."
                    className="w-full p-2.5 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                  />
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                    <div>
                      <label className="text-slate-500 text-xs block mb-1">Follow-up Instructions</label>
                      <input
                        type="text"
                        disabled={isFinalized && !amendMode}
                        placeholder="e.g. Return in 1 week if symptoms persist"
                        value={followUpInstructions}
                        onChange={(e) => setFollowUpInstructions(e.target.value)}
                        className="w-full p-2 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                      />
                    </div>
                    <div>
                      <label className="text-slate-500 text-xs block mb-1">Follow-up Date</label>
                      <input
                        type="date"
                        disabled={isFinalized && !amendMode}
                        value={followUpDate}
                        onChange={(e) => setFollowUpDate(e.target.value)}
                        className="w-full p-2 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700"
                      />
                    </div>
                  </div>

                  {/* Laboratory Order Request */}
                  <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                        <FaFlask className="text-blue-500" /> Integrated Laboratory Orders
                      </span>
                      {catalogTests.length > 0 && (
                        <select
                          disabled={isFinalized && !amendMode}
                          value={labPriority}
                          onChange={(e) => setLabPriority(e.target.value)}
                          className="text-xs p-1 rounded border dark:bg-slate-800 dark:border-slate-700"
                        >
                          <option value="ROUTINE">Priority: ROUTINE</option>
                          <option value="URGENT">Priority: URGENT</option>
                          <option value="STAT">Priority: STAT</option>
                        </select>
                      )}
                    </div>

                    {labOrderSuccess && (
                      <div className="mb-2 p-2 bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300 rounded text-xs">
                        {labOrderSuccess}
                      </div>
                    )}

                    {catalogTests.length === 0 ? (
                      <p className="text-slate-400 text-xs italic">Laboratory catalog is being initialized.</p>
                    ) : (
                      <div className="space-y-2">
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-32 overflow-y-auto p-2 bg-slate-50 dark:bg-slate-800/40 rounded-lg border border-slate-200 dark:border-slate-800">
                          {catalogTests.map((t) => {
                            const isSelected = selectedLabCodes.includes(t.testCode);
                            return (
                              <button
                                key={t.testCode}
                                type="button"
                                disabled={isFinalized && !amendMode}
                                onClick={() => {
                                  if (isSelected) {
                                    setSelectedLabCodes(selectedLabCodes.filter((c) => c !== t.testCode));
                                  } else {
                                    setSelectedLabCodes([...selectedLabCodes, t.testCode]);
                                  }
                                }}
                                className={`text-left p-2 rounded text-xs border transition-all ${
                                  isSelected
                                    ? "bg-blue-50 dark:bg-blue-900/40 border-blue-500 font-bold text-blue-700 dark:text-blue-300"
                                    : "bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300"
                                }`}
                              >
                                <div className="font-semibold">{t.testCode}</div>
                                <div className="text-[10px] text-slate-400 truncate">{t.testName}</div>
                              </button>
                            );
                          })}
                        </div>
                        {selectedLabCodes.length > 0 && (!isFinalized || amendMode) && (
                          <div className="flex justify-end">
                            <button
                              type="button"
                              onClick={handleOrderLabTests}
                              disabled={orderingLab}
                              className="btn btn-xs btn-primary flex items-center gap-1"
                            >
                              <FaFlask /> Order {selectedLabCodes.length} Selected Test(s)
                            </button>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                {/* Revision & Amendment History */}
                {encounter?.revisions && encounter.revisions.length > 1 && (
                  <div className="pt-4 border-t dark:border-slate-800">
                    <h4 className="text-xs font-bold text-slate-700 dark:text-slate-300 flex items-center gap-1 mb-2">
                      <FaHistory /> Revision & Amendment History ({encounter.revisions.length} revisions)
                    </h4>
                    <div className="space-y-2 max-h-40 overflow-y-auto">
                      {encounter.revisions.map((rev) => (
                        <div key={rev.id} className="p-2.5 bg-slate-50 dark:bg-slate-800/60 rounded-lg text-xs border border-slate-200 dark:border-slate-700">
                          <div className="flex justify-between items-center font-semibold">
                            <span>Revision #{rev.revisionNumber} ({rev.status})</span>
                            <span className="text-slate-400">{new Date(rev.signedAt || rev.createdAt).toLocaleString()}</span>
                          </div>
                          {rev.amendmentReason && (
                            <p className="text-amber-700 dark:text-amber-300 mt-1 italic">
                              Reason: {rev.amendmentReason}
                            </p>
                          )}
                          <p className="text-slate-600 dark:text-slate-300 mt-1">Diagnosis: {rev.diagnosisText || "None specified"}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Amendment Form if in Amend Mode */}
                {amendMode && (
                  <div className="p-4 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 rounded-xl space-y-3">
                    <h4 className="text-xs font-bold text-amber-800 dark:text-amber-200 uppercase">
                      New Clinical Addendum / Amendment
                    </h4>
                    <div>
                      <label className="text-xs font-medium text-slate-700 dark:text-slate-300 block mb-1">
                        Amendment Justification / Reason *
                      </label>
                      <input
                        type="text"
                        required
                        placeholder="State clear clinical reason for amendment (e.g. updated pathology report)..."
                        value={amendmentReason}
                        onChange={(e) => setAmendmentReason(e.target.value)}
                        className="w-full p-2 text-xs border rounded-lg dark:bg-slate-800 dark:border-slate-700 font-medium"
                      />
                    </div>
                    <div className="flex gap-2 justify-end">
                      <button
                        type="button"
                        onClick={() => setAmendMode(false)}
                        className="px-3 py-1.5 text-xs text-slate-600 dark:text-slate-300 hover:underline"
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        disabled={saving || !amendmentReason.trim()}
                        onClick={handleAmend}
                        className="px-4 py-1.5 text-xs bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg transition"
                      >
                        Save & Append Amendment
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        {/* Footer Actions */}
        <div className="px-6 py-4 bg-slate-100 dark:bg-slate-800/80 border-t border-slate-200 dark:border-slate-700 flex items-center justify-between">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 dark:text-slate-300 hover:text-slate-900"
          >
            Close Chart
          </button>

          <div className="flex items-center gap-3">
            {!isFinalized ? (
              <>
                <button
                  type="button"
                  disabled={saving || loading}
                  onClick={handleSaveDraft}
                  className="px-4 py-2 bg-slate-200 hover:bg-slate-300 dark:bg-slate-700 dark:hover:bg-slate-600 text-slate-800 dark:text-slate-100 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
                >
                  <FaSave /> Save Draft
                </button>
                <button
                  type="button"
                  disabled={saving || loading || !diagnosisText.trim()}
                  onClick={() => setConfirmSignModal(true)}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-lg flex items-center gap-1.5 shadow-sm transition"
                >
                  <FaCheckCircle /> Sign & Finalize
                </button>
              </>
            ) : (
              !amendMode && (
                <button
                  type="button"
                  disabled={saving || loading}
                  onClick={() => setAmendMode(true)}
                  className="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold rounded-lg flex items-center gap-1.5 transition"
                >
                  <FaHistory /> Add Amendment
                </button>
              )
            )}
          </div>
        </div>
      </div>

      {/* Confirmation Dialog Before Final Signing */}
      {confirmSignModal && (
        <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/70 p-4">
          <div className="bg-white dark:bg-slate-900 rounded-xl p-6 max-w-md w-full shadow-2xl border border-slate-200 dark:border-slate-800 space-y-4">
            <h4 className="text-base font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
              <FaLock className="text-blue-600" /> Confirm Clinical Record Signing
            </h4>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              Once finalized and signed, this clinical consultation note becomes legally immutable. Any future additions must be appended as traceable addenda. Are you ready to sign and release this record to the patient?
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => setConfirmSignModal(false)}
                className="px-3 py-1.5 text-xs text-slate-600 dark:text-slate-300"
              >
                Review Note Again
              </button>
              <button
                disabled={saving}
                onClick={handleSignFinalize}
                className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-lg"
              >
                Sign & Finalize
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
