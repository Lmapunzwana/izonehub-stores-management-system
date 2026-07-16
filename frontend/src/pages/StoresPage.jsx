import { useState, useEffect } from "react";
import { Plus, Check, X, MapPin, Users, AlertTriangle } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function StoresPage() {
  const { stores, addStore, refreshItems, users } = useAppData();
  const { showConfirm, showAlert } = useAppModal();
  const [showForm, setShowForm] = useState(false);
  const [newStore, setNewStore] = useState({ name: "", type: "SITE", location: "", managerId: "" });
  const [error, setError] = useState(null);
  const [sub, setSub] = useState(null);
  const [editingManagerId, setEditingManagerId] = useState(null); // storeId being edited
  const [newManagerId, setNewManagerId] = useState("");
  const [savingManager, setSavingManager] = useState(false);

  useEffect(() => {
    apiFetch("/api/subscription").then(setSub).catch(() => {});
  }, []);

  const operationalCount = sub?.operationalCount ?? stores.filter(s => s.active).length;
  const allowedSlots     = sub?.allowedStoreSlots ?? 0;
  const atCapacity       = allowedSlots > 0 && operationalCount >= allowedSlots;
  const capacityPct      = allowedSlots > 0 ? Math.round((operationalCount / allowedSlots) * 100) : 0;

  async function onSaveNewStore() {
    setError(null);
    if (!newStore.name.trim() || !newStore.location.trim() || !newStore.managerId) {
      setError("Name, Location, and Manager are required.");
      return;
    }
    if (atCapacity) {
      setError(`Store capacity reached (${operationalCount}/${allowedSlots}). Increase your subscription limit first.`);
      return;
    }
    try {
      await addStore({
        name:      newStore.name,
        type:      newStore.type,
        location:  newStore.location,
        managerId: newStore.managerId,
      });
      setNewStore({ name: "", type: "SITE", location: "", managerId: "" });
      setShowForm(false);
    } catch (e) {
      setError(e.message || "Failed to add store.");
    }
  }

  async function updateManager(storeId) {
    if (!newManagerId) return;
    setSavingManager(true);
    try {
      await apiFetch(`/api/stores/${storeId}/manager`, {
        method: "PUT",
        body: { managerId: newManagerId },
      });
      await refreshItems();
      setEditingManagerId(null);
      setNewManagerId("");
    } catch (e) {
      showAlert({ title: "Error", message: e.message || "Failed to update manager.", type: "danger" });
    } finally {
      setSavingManager(false);
    }
  }

  function initiateClosure(id) {
    showConfirm({
      title: "Initiate Closure",
      message: "Initiate closure for this store? No new operations will be allowed. All pending transactions must be resolved before it fully closes.",
      type: "warning",
      confirmText: "Yes, Close Store",
      onConfirm: async () => {
        try {
          await apiFetch(`/api/stores/${id}/close`, { method: "POST" });
          window.location.reload();
        } catch (e) {
          showAlert({ title: "Closure Failed", message: e.message || "Failed to initiate closure", type: "danger" });
        }
      },
    });
  }

  function reopenStore(id) {
    showConfirm({
      title: "Reopen Store",
      message: "Reopen this store?",
      type: "info",
      confirmText: "Yes, Reopen",
      onConfirm: async () => {
        try {
          await apiFetch(`/api/stores/${id}/reopen`, { method: "PUT" });
          window.location.reload();
        } catch (e) {
          showAlert({ title: "Reopen Failed", message: e.message || "Failed to reopen store", type: "danger" });
        }
      },
    });
  }

  // Derive store status from booleans (active/closing)
  function getStoreStatus(s) {
    if (!s.active && s.closing) return { label: "Pending Shutdown", type: "danger" };
    if (!s.active) return { label: "Closed", type: "default" };
    if (s.closing)  return { label: "Closing", type: "warning" };
    return { label: "Open", type: "success" };
  }

  const managerCandidates = users.filter(u =>
    u.active && (u.roles.includes("SITE_STORE_MANAGER") || u.roles.includes("CENTRAL_STORE_MANAGER"))
  );

  return (
    <div className="page">
      {/* Capacity indicator */}
      {allowedSlots > 0 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 16, color: "#0f172a" }}>Store Capacity</div>
              <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>
                {operationalCount} of {allowedSlots} operational store slots used
              </div>
            </div>
            <div style={{ fontSize: "1.8em", fontWeight: 700, color: atCapacity ? "#dc2626" : "#10b981" }}>
              {operationalCount}/{allowedSlots}
            </div>
          </div>
          <div style={{ height: 10, background: "#e2e8f0", borderRadius: 6, overflow: "hidden" }}>
            <div style={{
              height: "100%", borderRadius: 6,
              width: `${Math.min(capacityPct, 100)}%`,
              background: capacityPct >= 100 ? "#dc2626" : capacityPct >= 80 ? "#f59e0b" : "#10b981",
              transition: "width 0.5s ease",
            }} />
          </div>
          {atCapacity && (
            <div style={{ fontSize: 13, color: "#dc2626", marginTop: 8, fontWeight: 500, display: "flex", alignItems: "center", gap: 6 }}>
              <AlertTriangle size={14} /> Capacity reached. Increase your subscription limit to add more stores.
            </div>
          )}
        </div>
      )}

      <div className="card">
        <CardHeader
          title="Stores Management"
          actions={[
            {
              label: showForm ? "Close" : "Add Store",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => setShowForm(v => !v),
              disabled: atCapacity,
              title: atCapacity ? "Store capacity reached" : undefined,
            },
          ]}
        />

        {showForm && (
          <div className="form-grid" style={{ marginBottom: 20 }}>
            {error && (
              <div className="full" style={{ color: "#dc2626", background: "#fee2e2", padding: "8px 12px", borderRadius: 6, fontSize: 13 }}>
                {error}
              </div>
            )}
            <div>
              <label>Store Name</label>
              <input
                className="input"
                placeholder="e.g. Main Warehouse"
                value={newStore.name}
                onChange={e => setNewStore(f => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div>
              <label>Store Type</label>
              <select
                className="input"
                value={newStore.type}
                onChange={e => setNewStore(f => ({ ...f, type: e.target.value }))}
              >
                <option value="CENTRAL">Central Warehouse</option>
                <option value="SITE">Site Store</option>
              </select>
            </div>
            <div>
              <label>Location</label>
              <input
                className="input"
                placeholder="e.g. Mount Pleasant, Harare"
                value={newStore.location}
                onChange={e => setNewStore(f => ({ ...f, location: e.target.value }))}
              />
            </div>
            <div>
              <label>Store Manager</label>
              <select
                className="input"
                value={newStore.managerId}
                onChange={e => setNewStore(f => ({ ...f, managerId: e.target.value }))}
              >
                <option value="">Select a manager</option>
                {managerCandidates.map(u => (
                  <option key={u.id} value={u.id}>{u.fullName}</option>
                ))}
              </select>
            </div>

            <div className="full actions-row">
              {error && !error.includes("capacity") && <span style={{ color: "#dc2626", marginRight: "auto" }}>{error}</span>}
              <button className="btn" onClick={() => setShowForm(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={onSaveNewStore} disabled={atCapacity}>
                Save Store
              </button>
            </div>
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Store Name</th>
              <th>Type</th>
              <th>Location</th>
              <th>Status</th>
              <th>Manager</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {stores.map(s => {
              const status = getStoreStatus(s);
              return (
                <tr key={s.id}>
                  <td style={{ fontWeight: 500 }}>{s.name}</td>
                  <td>
                    <Badge type={s.type === "CENTRAL" ? "primary" : "default"}>{s.type}</Badge>
                  </td>
                  <td>
                    <MapPin size={14} style={{ display: "inline", verticalAlign: "middle", marginRight: 4, opacity: 0.5 }} />
                    {s.location}
                  </td>
                  <td>
                    <Badge type={status.type}>{status.label}</Badge>
                  </td>
                  <td>
                    {editingManagerId === s.id ? (
                      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                        <select
                          className="input"
                          style={{ padding: "4px 8px", fontSize: 12, width: 160 }}
                          value={newManagerId}
                          onChange={e => setNewManagerId(e.target.value)}
                        >
                          <option value="">Select…</option>
                          {managerCandidates.map(u => (
                            <option key={u.id} value={u.id}>{u.fullName}</option>
                          ))}
                        </select>
                        <button
                          className="btn btn-success"
                          style={{ padding: "4px 8px", fontSize: 12 }}
                          disabled={!newManagerId || savingManager}
                          onClick={() => updateManager(s.id)}
                        >
                          <Check size={12} />
                        </button>
                        <button
                          className="btn btn-outline"
                          style={{ padding: "4px 8px", fontSize: 12 }}
                          onClick={() => { setEditingManagerId(null); setNewManagerId(""); }}
                        >
                          <X size={12} />
                        </button>
                      </div>
                    ) : (
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span>{s.manager?.fullName || <span style={{ opacity: 0.5 }}>Unassigned</span>}</span>
                        <button
                          className="ch-btn ch-btn--ghost"
                          style={{ padding: "2px 6px", fontSize: 11 }}
                          onClick={() => { setEditingManagerId(s.id); setNewManagerId(s.manager?.id || ""); }}
                          title="Change manager"
                        >
                          <Users size={12} />
                        </button>
                      </div>
                    )}
                  </td>
                  <td>
                    <div className="action-buttons">
                      {s.active && !s.closing && (
                        <button className="ch-btn ch-btn--danger" onClick={() => initiateClosure(s.id)}>
                          <X size={14} />
                          Initiate Closure
                        </button>
                      )}
                      {!s.active && !s.closing && (
                        <button className="ch-btn ch-btn--outline" onClick={() => reopenStore(s.id)}>
                          <Check size={14} />
                          Reopen
                        </button>
                      )}
                      {s.closing && !s.active && (
                        <span style={{ color: "#64748b", fontSize: 13 }}>Pending shutdown…</span>
                      )}
                      {s.active && s.closing && (
                        <span style={{ color: "#f59e0b", fontWeight: 500, fontSize: 13 }}>Closing in progress</span>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {stores.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b", padding: "20px" }}>
                  No stores found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
