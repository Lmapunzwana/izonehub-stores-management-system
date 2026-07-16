import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { Edit, Plus, CheckCircle, Warehouse } from "lucide-react";
import { useAppData } from "../context/AppDataContext";

const STATUS_CYCLE = [
  { label: "In Transit", type: "warning" },
  { label: "Awaiting GRN", type: "info" },
  { label: "GRN Confirmed", type: "success" },
];

export default function ExpectedReceiptsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const lockedItemId   = location.state?.lockedItemId;
  const lockedItemName = location.state?.lockedItemName;

  const { expectedReceipts, addExpectedReceipt, advanceReceiptStatus, items, stores, defaultStoreId, user } = useAppData();

  const isCentralManager = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin          = user?.roles?.includes("SYSTEM_ADMINISTRATOR");

  // Central manager: locked to their assigned store.
  // Admin: can choose any CENTRAL store.
  const centralStores  = stores.filter(s => s.type === "CENTRAL" && s.active);
  const assignedStoreId = user?.assignedStoreId || defaultStoreId;

  const [showForm, setShowForm] = useState(!!lockedItemId);
  const [newReceipt, setNewReceipt] = useState({
    supplier: "",
    eta: "",
    itemId: lockedItemId || items[0]?.id || "",
    quantity: "",
    storeId: isCentralManager ? (assignedStoreId || "") : (centralStores[0]?.id || ""),
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  async function onUpdateStatus(receiptNo) {
    setBusyId(receiptNo);
    try {
      await advanceReceiptStatus(receiptNo);
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  }

  async function onSaveNewReceipt() {
    setError(null);
    if (!newReceipt.supplier.trim() || !newReceipt.itemId || !newReceipt.storeId || !newReceipt.quantity) {
      setError("Supplier, item, store, and quantity are all required.");
      return;
    }
    if (Number(newReceipt.quantity) <= 0) {
      setError("Expected quantity must be greater than zero.");
      return;
    }
    setSaving(true);
    try {
      await addExpectedReceipt(newReceipt);
      setNewReceipt({
        supplier: "",
        eta: "",
        itemId: lockedItemId || items[0]?.id || "",
        quantity: "",
        storeId: isCentralManager ? (assignedStoreId || "") : (centralStores[0]?.id || ""),
      });
      setShowForm(false);
    } catch (e) {
      setError(e?.message || "Failed to create expected receipt.");
    } finally {
      setSaving(false);
    }
  }

  // Determine the store name to display for a receipt
  function storeName(r) {
    const store = stores.find(s => s.id === r.original?.store?.id);
    return store?.name || r.original?.store?.name || "—";
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Expected Receipts"
          subtitle="Track incoming supplier shipments and confirm goods received"
          actions={[
            {
              label: showForm ? "Close" : "New Expected Receipt",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => { setShowForm(v => !v); setError(null); },
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
              <label>Supplier Name</label>
              <input
                className="input"
                placeholder="e.g. ABC Supplies Ltd"
                value={newReceipt.supplier}
                onChange={(e) => setNewReceipt(f => ({ ...f, supplier: e.target.value }))}
              />
            </div>

            <div>
              <label>Expected Date</label>
              <input
                className="input"
                type="date"
                value={newReceipt.eta}
                onChange={(e) => setNewReceipt(f => ({ ...f, eta: e.target.value }))}
              />
            </div>

            <div>
              <label>Destination Store</label>
              {isCentralManager ? (
                // Central manager: locked to their store
                <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 14px", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, color: "#475569" }}>
                  <Warehouse size={16} />
                  {stores.find(s => s.id === assignedStoreId)?.name || "Your assigned central store"}
                  <span style={{ marginLeft: "auto", fontSize: 12, color: "#94a3b8" }}>(locked)</span>
                </div>
              ) : (
                // Admin: choose any central store
                <select
                  className="input"
                  value={newReceipt.storeId}
                  onChange={(e) => setNewReceipt(f => ({ ...f, storeId: e.target.value }))}
                >
                  <option value="">Select a central store…</option>
                  {centralStores.map(s => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              )}
            </div>

            <div>
              <label>Item</label>
              {lockedItemId ? (
                <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 14px", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, color: "#475569" }}>
                  {lockedItemName || items.find(i => i.id === lockedItemId)?.name || lockedItemId}
                  <span style={{ marginLeft: "auto", fontSize: 12, color: "#94a3b8" }}>(locked)</span>
                </div>
              ) : (
                <select
                  className="input"
                  value={newReceipt.itemId}
                  onChange={(e) => setNewReceipt(f => ({ ...f, itemId: e.target.value }))}
                >
                  <option value="">Select an item…</option>
                  {items.map(i => (
                    <option key={i.id} value={i.id}>{i.name} ({i.code})</option>
                  ))}
                </select>
              )}
            </div>

            <div>
              <label>Expected Quantity</label>
              <input
                className="input"
                type="number"
                min="1"
                placeholder="e.g. 100"
                value={newReceipt.quantity}
                onChange={(e) => setNewReceipt(f => ({ ...f, quantity: e.target.value }))}
              />
            </div>

            <div className="full actions-row">
              <button className="btn" onClick={() => { setShowForm(false); setError(null); }}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={onSaveNewReceipt} disabled={saving}>
                {saving ? "Saving…" : "Save Receipt"}
              </button>
            </div>
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Receipt No</th>
              <th>Supplier</th>
              <th>Store</th>
              <th>ETA</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {expectedReceipts.map((r) => {
              const status = STATUS_CYCLE[r.statusIndex] || STATUS_CYCLE[0];
              const canAdvance = r.statusIndex === 0;
              return (
                <tr key={r.receiptNo}>
                  <td style={{ fontWeight: 600 }}>{r.receiptNo}</td>
                  <td>{r.supplier}</td>
                  <td style={{ color: "#64748b", fontSize: 13 }}>{storeName(r)}</td>
                  <td>{r.eta || "—"}</td>
                  <td>
                    <Badge type={status.type}>{status.label}</Badge>
                  </td>
                  <td>
                    {canAdvance && (
                      <button
                        className="ch-btn ch-btn--ghost"
                        disabled={busyId === r.receiptNo}
                        onClick={() => onUpdateStatus(r.receiptNo)}
                      >
                        <Edit size={16} />
                        {busyId === r.receiptNo ? "Updating…" : "Update Status"}
                      </button>
                    )}
                    {r.statusIndex === 1 && (
                      <button
                        className="ch-btn ch-btn--success"
                        onClick={() => navigate(`/confirm-grn?receiptId=${r.id}`)}
                      >
                        <CheckCircle size={16} />
                        Confirm GRN
                      </button>
                    )}
                    {r.statusIndex === 2 && (
                      <span style={{ color: "#64748b", fontSize: 13 }}>Complete</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {expectedReceipts.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No expected receipts yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
