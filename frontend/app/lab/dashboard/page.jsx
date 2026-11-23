"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import { getLabOrders } from "@/services/labService";
import { FaFlask, FaVial, FaCheckCircle, FaClock, FaExclamationTriangle } from "react-icons/fa";

export default function LabDashboard() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);
        const data = await getLabOrders("", 0, 50);
        setOrders(data.content || []);
      } catch (err) {
        setError(err.message || "Failed to load laboratory data");
      } finally {
        setLoading(false);
      }
    };
    fetchDashboardData();
  }, []);

  const totalOrders = orders.length;
  const pendingCollection = orders.filter((o) => o.status === "ORDERED" || o.status === "COLLECTION_PENDING").length;
  const inProgress = orders.filter((o) => o.status === "IN_PROGRESS").length;
  const completed = orders.filter((o) => o.status === "COMPLETED").length;
  const statOrders = orders.filter((o) => o.priority === "STAT" && o.status !== "COMPLETED" && o.status !== "CANCELLED").length;

  return (
    <ProtectedRoute allowedRoles={["LAB_TECH", "ADMIN", "DOCTOR"]}>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar title="Laboratory Information System" />
          <div className="content-area">
            {/* KPI Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
              <div className="card p-4 border-l-4 border-blue-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Total Active Orders</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{totalOrders}</div>
                </div>
                <FaFlask className="text-3xl text-blue-500 opacity-60" />
              </div>

              <div className="card p-4 border-l-4 border-amber-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Pending Specimen</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{pendingCollection}</div>
                </div>
                <FaVial className="text-3xl text-amber-500 opacity-60" />
              </div>

              <div className="card p-4 border-l-4 border-indigo-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Testing In Progress</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{inProgress}</div>
                </div>
                <FaClock className="text-3xl text-indigo-500 opacity-60" />
              </div>

              <div className="card p-4 border-l-4 border-emerald-500 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500 font-bold uppercase tracking-wider">Completed Reports</div>
                  <div className="text-2xl font-bold text-slate-800 dark:text-slate-100 mt-1">{completed}</div>
                </div>
                <FaCheckCircle className="text-3xl text-emerald-500 opacity-60" />
              </div>
            </div>

            {statOrders > 0 && (
              <div className="p-4 mb-6 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 rounded-xl flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <FaExclamationTriangle className="text-rose-500 text-xl" />
                  <span className="font-semibold text-rose-800 dark:text-rose-200">
                    High Priority: {statOrders} STAT laboratory order(s) pending immediate processing!
                  </span>
                </div>
                <Link href="/lab/orders?status=ORDERED" className="btn btn-sm btn-danger">
                  View STAT Queue
                </Link>
              </div>
            )}

            {/* Recent Orders Queue */}
            <div className="card p-6">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-bold text-lg text-slate-800 dark:text-slate-100">Live Laboratory Work Queue</h3>
                <Link href="/lab/orders" className="btn btn-sm btn-primary">
                  Open Full Queue
                </Link>
              </div>

              {loading ? (
                <div className="py-12 text-center text-slate-500">Loading laboratory work queue...</div>
              ) : error ? (
                <div className="py-8 text-center text-rose-500">{error}</div>
              ) : orders.length === 0 ? (
                <div className="py-12 text-center text-slate-400">No active laboratory orders found.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="table-default w-full">
                    <thead>
                      <tr>
                        <th>Order #</th>
                        <th>Patient</th>
                        <th>Tests</th>
                        <th>Priority</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {orders.slice(0, 8).map((order) => (
                        <tr key={order.id}>
                          <td className="font-mono text-xs font-semibold">{order.orderNumber}</td>
                          <td className="font-medium">{order.patientName}</td>
                          <td>
                            <div className="flex flex-wrap gap-1">
                              {order.orderedTests?.map((t) => (
                                <span key={t.testCode} className="badge badge-secondary text-xs">
                                  {t.testCode}
                                </span>
                              ))}
                            </div>
                          </td>
                          <td>
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
                          </td>
                          <td>
                            <span className="badge badge-info text-xs">{order.status}</span>
                          </td>
                          <td>
                            <Link href="/lab/orders" className="btn btn-xs btn-outline">
                              Process
                            </Link>
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
    </ProtectedRoute>
  );
}
