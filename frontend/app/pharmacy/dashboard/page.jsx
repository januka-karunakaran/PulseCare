"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getLowStockMedicines, getNearExpiryBatches, getPrescriptionQueue } from "@/services/pharmacyService";
import { FaPrescriptionBottleAlt, FaExclamationTriangle, FaBoxes, FaClipboardList, FaArrowRight } from "react-icons/fa";

export default function PharmacyDashboard() {
  const [lowStock, setLowStock] = useState([]);
  const [nearExpiry, setNearExpiry] = useState([]);
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        const [lowStockData, nearExpiryData, queueData] = await Promise.all([
          getLowStockMedicines(),
          getNearExpiryBatches(30),
          getPrescriptionQueue(0, 50),
        ]);
        setLowStock(lowStockData || []);
        setNearExpiry(nearExpiryData || []);
        setQueue(queueData?.content || []);
      } catch (err) {
        setError(err.message || "Failed to load pharmacy dashboard");
      } finally {
        setLoading(false);
      }
    };
    fetchDashboard();
  }, []);

  const pendingPrescriptions = queue.filter((q) => !q.fullyDispensed).length;

  return (
    <ProtectedRoute allowedRoles={["PHARMACIST", "ADMIN"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Pharmacy Management Dashboard" />
          <div className="content-area">
            {error && <div className="alert alert-error mb-4">{error}</div>}

            {/* KPI Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
              <div className="card p-4 border-l-4 border-blue-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Prescriptions in Queue</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{pendingPrescriptions}</div>
                </div>
                <FaClipboardList className="text-3xl text-blue-500 opacity-60" />
              </div>

              <div className="card p-4 border-l-4 border-amber-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Low Stock Medicines</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{lowStock.length}</div>
                </div>
                <FaExclamationTriangle className="text-3xl text-amber-500 opacity-60" />
              </div>

              <div className="card p-4 border-l-4 border-rose-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Near Expiry Batches (&lt;30d)</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{nearExpiry.length}</div>
                </div>
                <FaBoxes className="text-3xl text-rose-500 opacity-60" />
              </div>
            </div>

            {/* Quick Actions */}
            <div className="flex gap-4 mb-6">
              <Link href="/pharmacy/prescriptions" className="btn btn-primary flex items-center gap-2">
                <FaPrescriptionBottleAlt /> Dispense Prescriptions
              </Link>
              <Link href="/pharmacy/batches" className="btn btn-secondary flex items-center gap-2">
                <FaBoxes /> Receive & Manage Batches
              </Link>
              <Link href="/pharmacy/inventory" className="btn btn-secondary flex items-center gap-2">
                Catalog & Stock
              </Link>
            </div>

            {/* Tables Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Low Stock Alerts */}
              <div className="card p-4">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                    <FaExclamationTriangle className="text-amber-500" /> Low Stock Critical Alerts
                  </h3>
                  <Link href="/pharmacy/inventory" className="text-xs text-blue-600 hover:underline flex items-center gap-1">
                    Manage <FaArrowRight />
                  </Link>
                </div>
                {lowStock.length === 0 ? (
                  <div className="text-sm text-slate-500 py-4 text-center">All medicine stock levels are healthy.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b text-slate-500">
                          <th className="py-2">Code</th>
                          <th className="py-2">Generic Name</th>
                          <th className="py-2 text-right">Available</th>
                          <th className="py-2 text-right">Reorder Level</th>
                        </tr>
                      </thead>
                      <tbody>
                        {lowStock.slice(0, 5).map((m) => (
                          <tr key={m.id} className="border-b hover:bg-slate-50 dark:hover:bg-slate-800/50">
                            <td className="py-2 font-mono font-bold text-xs">{m.medicineCode}</td>
                            <td className="py-2">{m.genericName}</td>
                            <td className="py-2 text-right font-bold text-rose-600">{m.totalAvailableStock}</td>
                            <td className="py-2 text-right text-slate-500">{m.reorderLevel}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Near Expiry Alerts */}
              <div className="card p-4">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                    <FaBoxes className="text-rose-500" /> Near Expiry Batches
                  </h3>
                  <Link href="/pharmacy/batches" className="text-xs text-blue-600 hover:underline flex items-center gap-1">
                    View Batches <FaArrowRight />
                  </Link>
                </div>
                {nearExpiry.length === 0 ? (
                  <div className="text-sm text-slate-500 py-4 text-center">No batches are near expiry.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b text-slate-500">
                          <th className="py-2">Batch #</th>
                          <th className="py-2">Medicine</th>
                          <th className="py-2 text-right">Qty</th>
                          <th className="py-2 text-right">Expiry Date</th>
                        </tr>
                      </thead>
                      <tbody>
                        {nearExpiry.slice(0, 5).map((b) => (
                          <tr key={b.id} className="border-b hover:bg-slate-50 dark:hover:bg-slate-800/50">
                            <td className="py-2 font-mono text-xs">{b.batchNumber}</td>
                            <td className="py-2">{b.medicineName}</td>
                            <td className="py-2 text-right font-medium">{b.quantityAvailable}</td>
                            <td className="py-2 text-right text-rose-600 font-medium">
                              {b.expiryDate ? b.expiryDate.substring(0, 10) : "N/A"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
