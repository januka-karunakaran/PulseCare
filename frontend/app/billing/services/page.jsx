"use client";

import { useEffect, useState, useCallback } from "react";
import ProtectedRoute from "@/app/components/ProtectedRoute";
import Sidebar from "@/app/components/Sidebar";
import Topbar from "@/app/components/Topbar";
import {
  getCatalogServices,
  createCatalogService,
  updateCatalogService,
} from "@/services/billingService";
import {
  FaPlus,
  FaEdit,
  FaCheckCircle,
  FaTimesCircle,
  FaSync,
  FaSearch,
} from "react-icons/fa";

const CATEGORIES = [
  "CONSULTATION",
  "LABORATORY",
  "PHARMACY",
  "INPATIENT_BED",
  "PROCEDURE",
  "OTHER",
];

export default function BillingServicesPage() {
  const [services, setServices] = useState([]);
  const [filteredServices, setFilteredServices] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingService, setEditingService] = useState(null);
  const [error, setError] = useState("");
  const [formError, setFormError] = useState("");

  const [formData, setFormData] = useState({
    serviceCode: "",
    serviceName: "",
    category: "CONSULTATION",
    description: "",
    basePrice: "",
    taxRate: "0.00",
    active: true,
  });

  const loadServices = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const data = await getCatalogServices(selectedCategory);
      setServices(data || []);
    } catch (err) {
      setError(err.message || "Failed to load service catalog");
    } finally {
      setLoading(false);
    }
  }, [selectedCategory]);

  useEffect(() => {
    loadServices();
  }, [loadServices]);

  useEffect(() => {
    let filtered = services;
    if (search.trim()) {
      const q = search.toLowerCase();
      filtered = filtered.filter(
        (s) =>
          s.serviceCode?.toLowerCase().includes(q) ||
          s.serviceName?.toLowerCase().includes(q)
      );
    }
    setFilteredServices(filtered);
  }, [services, search]);

  const handleOpenCreate = () => {
    setEditingService(null);
    setFormData({
      serviceCode: "",
      serviceName: "",
      category: "CONSULTATION",
      description: "",
      basePrice: "",
      taxRate: "0.00",
      active: true,
    });
    setFormError("");
    setModalOpen(true);
  };

  const handleOpenEdit = (svc) => {
    setEditingService(svc);
    setFormData({
      serviceCode: svc.serviceCode,
      serviceName: svc.serviceName,
      category: svc.category,
      description: svc.description || "",
      basePrice: svc.basePrice,
      taxRate: svc.taxRate || "0.00",
      active: svc.active,
    });
    setFormError("");
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError("");
    try {
      if (editingService) {
        await updateCatalogService(editingService.id, {
          serviceName: formData.serviceName,
          category: formData.category,
          description: formData.description,
          basePrice: parseFloat(formData.basePrice),
          taxRate: parseFloat(formData.taxRate),
          active: formData.active,
        });
      } else {
        await createCatalogService({
          serviceCode: formData.serviceCode,
          serviceName: formData.serviceName,
          category: formData.category,
          description: formData.description,
          basePrice: parseFloat(formData.basePrice),
          taxRate: parseFloat(formData.taxRate),
          active: formData.active,
        });
      }
      setModalOpen(false);
      loadServices();
    } catch (err) {
      setFormError(err.message || "Failed to save service item");
    }
  };

  return (
    <ProtectedRoute allowedRoles={["ADMIN", "BILLING_STAFF"]}>
      <div className="app-layout">
        <Sidebar />
        <div className="main-content">
          <Topbar />
          <div className="content-container">
            {/* Header */}
            <div className="flex justify-between items-center mb-6">
              <div>
                <h1 className="text-2xl font-bold">Billing Service Catalog</h1>
                <p className="text-muted text-sm">
                  Configured billable services, clinical order fee codes, and standard tax rates
                </p>
              </div>
              <button onClick={handleOpenCreate} className="btn btn-primary flex items-center gap-2">
                <FaPlus /> Add New Service
              </button>
            </div>

            {error && <div className="alert alert-error mb-4">{error}</div>}

            {/* Filter bar */}
            <div className="card p-4 mb-6 flex flex-wrap gap-4 items-center justify-between">
              <div className="flex gap-4 items-center flex-1 max-w-md">
                <div className="relative w-full">
                  <FaSearch className="absolute left-3 top-3 text-muted" />
                  <input
                    type="text"
                    placeholder="Search by code or name..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="input pl-9 w-full"
                  />
                </div>
              </div>

              <div className="flex gap-3 items-center">
                <select
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                  className="input"
                >
                  <option value="">All Categories</option>
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c.replace("_", " ")}
                    </option>
                  ))}
                </select>
                <button
                  onClick={loadServices}
                  className="btn btn-secondary flex items-center gap-1"
                  disabled={loading}
                >
                  <FaSync className={loading ? "animate-spin" : ""} />
                </button>
              </div>
            </div>

            {/* Table */}
            <div className="card">
              <div className="overflow-x-auto">
                <table className="table w-full">
                  <thead>
                    <tr>
                      <th>Service Code</th>
                      <th>Name</th>
                      <th>Category</th>
                      <th>Base Price</th>
                      <th>Tax Rate</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredServices.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="text-center p-8 text-muted">
                          {loading ? "Loading service catalog..." : "No service catalog items found."}
                        </td>
                      </tr>
                    ) : (
                      filteredServices.map((svc) => (
                        <tr key={svc.id}>
                          <td className="font-mono font-bold text-primary">{svc.serviceCode}</td>
                          <td>
                            <div className="font-semibold">{svc.serviceName}</div>
                            {svc.description && (
                              <div className="text-xs text-muted truncate max-w-xs">
                                {svc.description}
                              </div>
                            )}
                          </td>
                          <td>
                            <span className="badge badge-gray">{svc.category}</span>
                          </td>
                          <td className="font-semibold">${Number(svc.basePrice || 0).toFixed(2)}</td>
                          <td>{Number(svc.taxRate || 0).toFixed(2)}%</td>
                          <td>
                            {svc.active ? (
                              <span className="badge badge-green flex items-center gap-1 w-fit">
                                <FaCheckCircle /> Active
                              </span>
                            ) : (
                              <span className="badge badge-red flex items-center gap-1 w-fit">
                                <FaTimesCircle /> Inactive
                              </span>
                            )}
                          </td>
                          <td>
                            <button
                              onClick={() => handleOpenEdit(svc)}
                              className="btn btn-sm btn-secondary flex items-center gap-1"
                            >
                              <FaEdit /> Edit
                            </button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Modal */}
            {modalOpen && (
              <div className="modal-backdrop">
                <div className="modal-card max-w-lg">
                  <h3 className="text-xl font-bold mb-4">
                    {editingService ? "Edit Service Item" : "Create New Billable Service"}
                  </h3>
                  {formError && <div className="alert alert-error mb-4">{formError}</div>}
                  <form onSubmit={handleSubmit}>
                    <div className="space-y-4">
                      <div>
                        <label className="label">Service Code</label>
                        <input
                          type="text"
                          required
                          disabled={!!editingService}
                          value={formData.serviceCode}
                          onChange={(e) =>
                            setFormData({ ...formData, serviceCode: e.target.value.toUpperCase() })
                          }
                          className="input w-full font-mono"
                          placeholder="e.g. CNS-GEN-01"
                        />
                      </div>
                      <div>
                        <label className="label">Service Name</label>
                        <input
                          type="text"
                          required
                          value={formData.serviceName}
                          onChange={(e) =>
                            setFormData({ ...formData, serviceName: e.target.value })
                          }
                          className="input w-full"
                          placeholder="General Consultation"
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="label">Category</label>
                          <select
                            value={formData.category}
                            onChange={(e) =>
                              setFormData({ ...formData, category: e.target.value })
                            }
                            className="input w-full"
                          >
                            {CATEGORIES.map((c) => (
                              <option key={c} value={c}>
                                {c.replace("_", " ")}
                              </option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <label className="label">Base Price ($)</label>
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            required
                            value={formData.basePrice}
                            onChange={(e) =>
                              setFormData({ ...formData, basePrice: e.target.value })
                            }
                            className="input w-full"
                            placeholder="50.00"
                          />
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="label">Tax Rate (%)</label>
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={formData.taxRate}
                            onChange={(e) =>
                              setFormData({ ...formData, taxRate: e.target.value })
                            }
                            className="input w-full"
                            placeholder="0.00"
                          />
                        </div>
                        <div className="flex items-center pt-6">
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={formData.active}
                              onChange={(e) =>
                                setFormData({ ...formData, active: e.target.checked })
                              }
                              className="checkbox"
                            />
                            <span className="font-semibold text-sm">Active for billing</span>
                          </label>
                        </div>
                      </div>
                      <div>
                        <label className="label">Description</label>
                        <textarea
                          value={formData.description}
                          onChange={(e) =>
                            setFormData({ ...formData, description: e.target.value })
                          }
                          className="input w-full"
                          rows={2}
                          placeholder="Standard outpatient clinical consultation"
                        />
                      </div>
                    </div>
                    <div className="flex justify-end gap-3 mt-6">
                      <button
                        type="button"
                        onClick={() => setModalOpen(false)}
                        className="btn btn-secondary"
                      >
                        Cancel
                      </button>
                      <button type="submit" className="btn btn-primary">
                        {editingService ? "Update Service" : "Create Service"}
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
