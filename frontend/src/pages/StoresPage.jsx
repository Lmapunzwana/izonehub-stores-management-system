import { useState } from "react";
import { Plus, Check, X, MapPin } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function StoresPage() {
  const { stores, addStore, refreshItems } = useAppData();
  const { showConfirm, showAlert } = useAppModal();
  const [showForm, setShowForm] = useState(false);
  const [newStore, setNewStore] = useState({ name: "", type: "SITE", location: "" });
  const [error, setError] = useState(null);

  async function onSaveNewStore() {
    setError(null);
    if (!newStore.name.trim() || !newStore.location.trim()) {
      setError("Name and Location are required.");
      return;
    }
    try {
      await addStore({
        name: newStore.name,
        type: newStore.type,
        location: newStore.location,
      });
      setNewStore({ name: "", type: "SITE", location: "" });
      setShowForm(false);
    } catch (e) {
      setError(e.message || "Failed to add store.");
    }
  }

  function initiateClosure(id) {
    showConfirm({
      title: "Initiate Closure",
      message: "Initiate closure for this store? This will generate a return for all items and restrict new operations.",
      type: "warning",
      confirmText: "Yes, Close Store",
      onConfirm: async () => {
        try {
          await apiFetch(`/api/stores/${id}/close`, { method: "POST" });
          window.location.reload();
        } catch (e) {
          showAlert({ title: "Closure Failed", message: e.message || "Failed to initiate closure", type: "danger" });
        }
      }
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
      }
    });
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Stores Management"
          actions={[
            {
              label: showForm ? "Close" : "Add Store",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => setShowForm((v) => !v),
            },
          ]}
        />

        {showForm && (
          <div className="form-grid" style={{ marginBottom: 20 }}>
            <div>
              <label>Store Name</label>
              <input
                className="input"
                placeholder="e.g. Main Warehouse"
                value={newStore.name}
                onChange={(e) => setNewStore((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div>
              <label>Store Type</label>
              <select
                className="input"
                value={newStore.type}
                onChange={(e) => setNewStore((f) => ({ ...f, type: e.target.value }))}
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
                onChange={(e) => setNewStore((f) => ({ ...f, location: e.target.value }))}
              />
            </div>
            
            <div className="full actions-row">
              {error && <span style={{ color: "#dc2626", marginRight: "auto" }}>{error}</span>}
              <button className="btn" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={onSaveNewStore}>
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
            {stores.map((s) => (
              <tr key={s.id}>
                <td style={{ fontWeight: 500 }}>{s.name}</td>
                <td>
                  <Badge type={s.type === "CENTRAL" ? "primary" : "default"}>
                    {s.type}
                  </Badge>
                </td>
                <td>
                  <MapPin size={14} style={{ display: "inline", verticalAlign: "middle", marginRight: 4, opacity: 0.5 }} />
                  {s.location}
                </td>
                <td>
                  <Badge type={s.active ? (s.closing ? "warning" : "success") : "danger"}>
                    {s.active ? (s.closing ? "Closing" : "Open") : "Closed"}
                  </Badge>
                </td>
                <td>{s.manager?.fullName || <span style={{opacity: 0.5}}>Unassigned</span>}</td>
                <td>
                  {s.active && !s.closing && (
                    <button 
                      className="ch-btn ch-btn--danger" 
                      onClick={() => initiateClosure(s.id)}
                    >
                      <X size={14} />
                      Initiate Closure
                    </button>
                  )}
                  {!s.active && (
                    <button 
                      className="ch-btn ch-btn--outline" 
                      onClick={() => reopenStore(s.id)}
                    >
                      <Check size={14} />
                      Reopen
                    </button>
                  )}
                </td>
              </tr>
            ))}
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
