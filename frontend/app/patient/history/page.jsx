"use client";

import { useEffect, useState } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import SilentLogin from "@/app/components/SilentLogin";
import { getClinicalTimeline, getEncounterById } from "@/services/clinicalService";
import { getMedicalHistory } from "@/services/patientDashboardService";
import { getUser, getAccessToken } from "@/utils/auth";
import { downloadLabReportPdf } from "@/services/labService";
import { FaFileMedical, FaCalendarCheck, FaStethoscope, FaTimes, FaHeartbeat, FaLock, FaFilePdf, FaFlask, FaPrescriptionBottleAlt } from "react-icons/fa";

export default function MedicalHistoryPage() {
  const user = getUser();
  const token = getAccessToken();
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedEncounter, setSelectedEncounter] = useState(null);
  const [loadingEncounter, setLoadingEncounter] = useState(false);

  useEffect(() => {
    const loadHistory = async () => {
      if (!user?.userId || !token) return;
      try {
        setLoading(true);
        setError("");
        let data;
        try {
          data = await getClinicalTimeline(user.userId);
        } catch {
          // Fallback to basic medical history if clinical timeline endpoint unavailable
          data = await getMedicalHistory(user.userId);
        }
        setHistory(data || []);
      } catch (err) {
        setError(err.message || "Failed to load medical history");
      } finally {
        setLoading(false);
      }
    };

    loadHistory();
  }, [user?.userId, token]);

  const handleViewReport = async (record) => {
    if (record.type === "CLINICAL_ENCOUNTER" || record.encounterId) {
      const idToFetch = record.encounterId || record.id;
      try {
        setLoadingEncounter(true);
        const encData = await getEncounterById(idToFetch);
        setSelectedEncounter(encData);
      } catch (err) {
        alert(err.message || "Failed to load clinical encounter details");
      } finally {
        setLoadingEncounter(false);
      }
    }
  };

  const handleDownloadLabPdf = async (labId) => {
    try {
      await downloadLabReportPdf(labId);
    } catch (err) {
      alert("Failed to download official lab report: " + err.message);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <ProtectedRoute allowedRoles={["PATIENT"]}>
      <SilentLogin />
      <div className="flex min-h-screen bg-slate-50 dark:bg-slate-950 transition-colors">
        <Sidebar />
        <main className="flex-1 p-6">
          <Topbar />

          <div className="mt-8 mb-8">
            <h1 className="text-3xl font-bold text-slate-900 dark:text-slate-100">Medical History</h1>
            <p className="text-slate-500 dark:text-slate-400 font-medium">A complete timeline of your finalized health records and encounters.</p>
          </div>

          {error && (
            <div className="max-w-4xl mx-auto mb-6 p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-2xl text-sm">
              {error}
            </div>
          )}

          <div className="max-w-4xl mx-auto">
            {history.length === 0 ? (
              <div className="bg-white dark:bg-slate-900 p-12 rounded-3xl text-center border border-slate-200 dark:border-slate-800 shadow-sm">
                <FaFileMedical className="mx-auto text-5xl text-slate-300 mb-4" />
                <p className="text-slate-500 font-medium">No medical records found in your history.</p>
              </div>
            ) : (
              <div className="relative space-y-8 before:absolute before:inset-0 before:ml-5 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-slate-300 dark:before:via-slate-700 before:to-transparent">
                {history.map((record) => (
                  <div key={record.id} className={`relative flex items-center justify-between md:justify-normal md:odd:flex-row-reverse group is-active`}>
                    {/* Icon */}
                    <div className="flex items-center justify-center w-10 h-10 rounded-full border border-white dark:border-slate-800 bg-slate-200 dark:bg-slate-800 text-slate-500 dark:text-slate-400 shadow shrink-0 md:order-1 md:group-odd:-translate-x-1/2 md:group-even:translate-x-1/2 transition-all group-hover:scale-110 group-hover:bg-blue-600 group-hover:text-white z-10">
                      {record.type === 'APPOINTMENT' ? <FaCalendarCheck /> : record.type === 'LAB_RESULT' ? <FaFlask /> : record.type === 'MEDICATION_DISPENSED' ? <FaPrescriptionBottleAlt /> : <FaFileMedical />}
                    </div>
                    {/* Content Card */}
                    <div className="w-[calc(100%-4rem)] md:w-[calc(50%-2.5rem)] bg-white dark:bg-slate-900 p-6 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-all hover:border-blue-500/50">
                      <div className="flex items-center justify-between mb-2">
                        <time className="font-bold text-blue-600 text-sm uppercase tracking-wider">{record.date}</time>
                        <span className={`text-[10px] font-bold px-2 py-1 rounded-full ${
                          record.type === 'APPOINTMENT' ? 'bg-amber-100 text-amber-700' :
                          record.type === 'CLINICAL_ENCOUNTER' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' :
                          record.type === 'LAB_RESULT' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300' :
                          record.type === 'MEDICATION_DISPENSED' ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300' :
                          'bg-emerald-100 text-emerald-700'
                        }`}>
                          {record.type}
                        </span>
                      </div>
                      <h3 className="text-lg font-bold text-slate-800 dark:text-slate-100 mb-1">{record.title}</h3>
                      <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400 mb-3">
                        <FaStethoscope />
                        <span>{record.doctorName}</span>
                      </div>
                      <p className="text-slate-600 dark:text-slate-400 text-sm italic">"{record.description}"</p>
                      <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-800 flex justify-between items-center">
                         <span className="text-xs font-bold text-slate-400">Status: {record.status}</span>
                         {(record.type === 'CLINICAL_ENCOUNTER' || record.encounterId) && (
                           <button
                             type="button"
                             onClick={() => handleViewReport(record)}
                             className="text-xs font-bold text-blue-600 hover:underline"
                           >
                             View Clinical Note
                           </button>
                         )}
                         {record.type === 'LAB_RESULT' && (
                           <button
                             type="button"
                             onClick={() => handleDownloadLabPdf(record.id)}
                             className="text-xs font-bold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                           >
                             <FaFilePdf className="text-rose-500" /> Download PDF Report
                           </button>
                         )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Details Modal for Finalized Clinical Encounter */}
          {selectedEncounter && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
              <div className="bg-white dark:bg-slate-900 max-w-2xl w-full rounded-2xl shadow-2xl border border-slate-200 dark:border-slate-800 overflow-hidden my-8">
                <div className="p-4 bg-slate-100 dark:bg-slate-800 flex justify-between items-center border-b dark:border-slate-700">
                  <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                    <FaLock className="text-blue-600" /> Finalized Clinical Record
                  </h3>
                  <button
                    onClick={() => setSelectedEncounter(null)}
                    className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                  >
                    <FaTimes />
                  </button>
                </div>
                <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto text-xs">
                  <div className="bg-slate-50 dark:bg-slate-800/40 p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex justify-between">
                    <div>
                      <span className="text-slate-400 block">Attending Clinician</span>
                      <span className="font-semibold text-slate-700 dark:text-slate-200">{selectedEncounter.signedByDoctorName || selectedEncounter.doctorName}</span>
                    </div>
                    <div>
                      <span className="text-slate-400 block">Signed At</span>
                      <span className="font-semibold text-slate-700 dark:text-slate-200">{selectedEncounter.signedAt ? new Date(selectedEncounter.signedAt).toLocaleString() : "Finalized"}</span>
                    </div>
                  </div>

                  {selectedEncounter.currentNote && (
                    <div className="space-y-3">
                      <div>
                        <span className="font-bold text-blue-600 uppercase tracking-wider block mb-1">Assessment & Diagnosis</span>
                        <div className="p-3 bg-blue-50 dark:bg-blue-950/30 rounded-xl font-semibold text-slate-800 dark:text-slate-200 border border-blue-100 dark:border-blue-900/50">
                          {selectedEncounter.currentNote.diagnosisText || "Routine Clinical Evaluation"} ({selectedEncounter.currentNote.diagnosisStatus || "CONFIRMED"})
                        </div>
                      </div>

                      {selectedEncounter.currentNote.subjective && (
                        <div>
                          <span className="font-bold text-slate-500 uppercase tracking-wider block mb-1">Reported Symptoms</span>
                          <p className="text-slate-700 dark:text-slate-300 leading-relaxed bg-slate-50 dark:bg-slate-800/40 p-3 rounded-xl border dark:border-slate-800">
                            {selectedEncounter.currentNote.subjective}
                          </p>
                        </div>
                      )}

                      {selectedEncounter.currentNote.plan && (
                        <div>
                          <span className="font-bold text-slate-500 uppercase tracking-wider block mb-1">Treatment Plan & Follow-up</span>
                          <p className="text-slate-700 dark:text-slate-300 leading-relaxed bg-slate-50 dark:bg-slate-800/40 p-3 rounded-xl border dark:border-slate-800">
                            {selectedEncounter.currentNote.plan}
                          </p>
                          {selectedEncounter.currentNote.followUpInstructions && (
                            <p className="mt-2 text-slate-600 dark:text-slate-400 italic">
                              Instructions: {selectedEncounter.currentNote.followUpInstructions}
                            </p>
                          )}
                        </div>
                      )}
                    </div>
                  )}

                  {selectedEncounter.observations && selectedEncounter.observations.length > 0 && (
                    <div className="pt-3 border-t dark:border-slate-800">
                      <span className="font-bold text-slate-500 uppercase tracking-wider block mb-2 flex items-center gap-1.5">
                        <FaHeartbeat className="text-rose-500" /> Recorded Vital Signs
                      </span>
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
                        {selectedEncounter.observations[0].systolicBp && (
                          <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded-lg">
                            <span className="text-slate-400 block text-[10px]">Blood Pressure</span>
                            <span className="font-bold text-slate-700 dark:text-slate-200">
                              {selectedEncounter.observations[0].systolicBp}/{selectedEncounter.observations[0].diastolicBp} mmHg
                            </span>
                          </div>
                        )}
                        {selectedEncounter.observations[0].heartRate && (
                          <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded-lg">
                            <span className="text-slate-400 block text-[10px]">Heart Rate</span>
                            <span className="font-bold text-slate-700 dark:text-slate-200">
                              {selectedEncounter.observations[0].heartRate} bpm
                            </span>
                          </div>
                        )}
                        {selectedEncounter.observations[0].temperature && (
                          <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded-lg">
                            <span className="text-slate-400 block text-[10px]">Temperature</span>
                            <span className="font-bold text-slate-700 dark:text-slate-200">
                              {selectedEncounter.observations[0].temperature} °C
                            </span>
                          </div>
                        )}
                        {selectedEncounter.observations[0].oxygenSaturation && (
                          <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded-lg">
                            <span className="text-slate-400 block text-[10px]">SpO2</span>
                            <span className="font-bold text-slate-700 dark:text-slate-200">
                              {selectedEncounter.observations[0].oxygenSaturation} %
                            </span>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
                <div className="p-4 bg-slate-100 dark:bg-slate-800 flex justify-end border-t dark:border-slate-700">
                  <button
                    onClick={() => setSelectedEncounter(null)}
                    className="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-semibold"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </ProtectedRoute>
  );
}
