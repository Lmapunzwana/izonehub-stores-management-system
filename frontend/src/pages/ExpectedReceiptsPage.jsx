import { useState } from "react";
import { useNavigate } from "react-router-dom";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { Edit, Plus, CheckCircle } from "lucide-react";
import { useAppData } from "../context/AppDataContext";

const STATUS_CYCLE = [
  { label: "In Transit", type: "warning" },
  { label: "Awaiting GRN", type: "info" },
  { label: "GRN Confirmed", type: "success" },
];

export default function ExpectedReceiptsPage() {
  const navigate = useNavigate();
  const { expectedReceipts, addExpectedReceipt, advanceReceiptStatus, items, stores, defaultStoreId } = useAppData();
  const [showForm, setShowForm] = useState(false);
  const activeStores = stores.filter((s) => s.active);
  const [newReceipt, setNewReceipt] = useState({
    supplier: "",
    eta: "",
    itemId: items[0]?.id || "",
    quantity: 100,
    storeId: defaultStoreId || "",
  });

  function onSaveNewReceipt() {
    if (!newReceipt.supplier.trim() || !newReceipt.itemId || !newReceipt.storeId) return;
    addExpectedReceipt(newReceipt);
    setNewReceipt({ supplier: "", eta: "", itemId: items[0]?.id || "", quantity: 100, storeId: defaultStoreId || "" });
    setShowForm(false);
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Expected Receipts"
          actions={[
            {
              label: showForm ? "Close" : "New Expected Receipt",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => setShowForm((v) => !v),
            },
          ]}
        />

        {showForm && (
          <div className="form-grid" style={{ marginBottom: 20 }}>
            <div>
              <label>Supplier</label>
              <input
                className="input"
                placeholder="Supplier name"
                value={newReceipt.supplier}
                onChange={(e) => setNewReceipt((f) => ({ ...f, supplier: e.target.value }))}
              />
            </div>
            <div>
              <label>Expected Date</label>
              <input
                className="input"
                type="date"
                value={newReceipt.eta}
                onChange={(e) => setNewReceipt((f) => ({ ...f, eta: e.target.value }))}
              />
            </div>
            <div>
              <label>Destination Store</label>
              <select
                className="input"
                value={newReceipt.storeId}
                onChange={(e) => setNewReceipt((f) => ({ ...f, storeId: e.target.value }))}
              >
                <option value="">Select a store…</option>
                {activeStores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.type === "CENTRAL" ? "Central" : "Site"})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Item</label>
              <select
                className="input"
                value={newReceipt.itemId}
                onChange={(e) => setNewReceipt((f) => ({ ...f, itemId: e.target.value }))}
              >
                {items.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name} ({i.code})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label>Expected Quantity</label>
              <input
                className="input"
                type="number"
                value={newReceipt.quantity}
                onChange={(e) => setNewReceipt((f) => ({ ...f, quantity: e.target.value }))}
              />
            </div>
            <div className="full actions-row">
              <button className="btn" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={onSaveNewReceipt}>
                Save Receipt
              </button>
            </div>
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Receipt No</th>
              <th>Supplier</th>
              <th>ETA</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {expectedReceipts.map((r) => {
              const status = STATUS_CYCLE[r.statusIndex] || STATUS_CYCLE[0];
              // PATCH /{id}/status can't advance past AWAITING_GRN — the
              // next step is Confirm GRN (a different endpoint entirely).
              const canAdvance = r.statusIndex === 0;
              return (
                <tr key={r.receiptNo}>
                  <td>{r.receiptNo}</td>
                  <td>{r.supplier}</td>
                  <td>{r.eta}</td>
                  <td>
                    <Badge type={status.type}>{status.label}</Badge>
                  </td>
                  <td>
                    {canAdvance && (
                      <button
                        className="ch-btn ch-btn--ghost"
                        onClick={() => advanceReceiptStatus(r.receiptNo)}
                      >
                        <Edit size={16} />
                        Update Status
                      </button>
                    )}
                    {r.statusIndex === 1 && (
                      <button
                        className="ch-btn ch-btn--success"
                        onClick={() => navigate("/confirm-grn")}
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
                <td colSpan={5} style={{ textAlign: "center", color: "#64748b" }}>
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
