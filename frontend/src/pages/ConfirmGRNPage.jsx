import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle, CheckCircle2, X, AlertTriangle } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

export default function ConfirmGRNPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedId = searchParams.get("receiptId");

  const { expectedReceipts, confirmGRN } = useAppData();

  // Only show receipts awaiting GRN
  const pending = expectedReceipts.filter(r => r.statusIndex === 1);

  const [receiptId, setReceiptId] = useState(preselectedId || pending[0]?.id || "");
  const [counted, setCounted] = useState({});   // lineId → value
  const [condition, setCondition] = useState({}); // lineId → GOOD/DAMAGED/SHORT
  const [confirmed, setConfirmed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  // If preselected ID from query param, honor it
  useEffect(() => {
    if (preselectedId) setReceiptId(preselectedId);
    else if (!receiptId && pending.length) setReceiptId(pending[0].id);
  }, [pending, preselectedId]);

  const receipt = expectedReceipts.find(r => r.id === receiptId);
  const lines   = receipt?.original?.lines || [];

  function onCancel() {
    navigate("/expected-receipts");
  }

  async function onConfirm() {
    if (!receipt) return;
    setError(null);
    setSaving(true);
    try {
      const overrides = lines
        .filter(l => counted[l.id] !== undefined && counted[l.id] !== "")
        .map(l => ({
          lineId: l.id,
          receivedQuantity: Number(counted[l.id]),
          condition: condition[l.id] || "GOOD",
        }));
      await confirmGRN(receipt.id, overrides);
      setConfirmed(true);
      setTimeout(() => navigate("/expected-receipts"), 1200);
    } catch (e) {
      setError(e?.message || "Failed to confirm GRN. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  if (!pending.length) {
    return (
      <div className="page">
        <div className="card">
          <CardHeader title="Confirm Goods Received Note" status={{ label: "Nothing pending", variant: "success" }} />
          <p style={{ color: "#64748b" }}>
            No expected receipts are currently awaiting GRN confirmation.
          </p>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate("/expected-receipts")}>
            Back to Expected Receipts
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Confirm Goods Received Note"
          status={
            confirmed
              ? { label: "Confirmed", variant: "success" }
              : { label: "Awaiting Physical Count", variant: "warning" }
          }
        />

        {/* Locked header fields */}
        <div className="form-grid" style={{ marginBottom: 8 }}>
          <div>
            <label>Expected Receipt</label>
            {pending.length === 1 ? (
              // Only one receipt — show locked
              <div style={{ padding: "10px 14px", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, color: "#475569", fontWeight: 600 }}>
                {receipt?.receiptNo} — {receipt?.supplier}
              </div>
            ) : (
              <select
                className="input"
                value={receiptId}
                onChange={(e) => {
                  setReceiptId(e.target.value);
                  setCounted({});
                  setCondition({});
                }}
              >
                {pending.map(r => (
                  <option key={r.id} value={r.id}>
                    {r.receiptNo} — {r.supplier}
                  </option>
                ))}
              </select>
            )}
          </div>

          <div>
            <label>Supplier</label>
            <div style={{ padding: "10px 14px", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, color: "#475569" }}>
              {receipt?.supplier || "—"}
            </div>
          </div>
        </div>

        <hr className="divider" />

        <h2 className="card-title">Physical Count Entry</h2>
        <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
          Enter the actual quantity counted for each line. Leave blank to accept the expected quantity in full.
          Any variance will automatically create a discrepancy and freeze the difference.
        </p>

        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Expected Qty</th>
              <th>Actual Counted</th>
              <th>Condition</th>
              <th>Variance</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => {
              const countedVal = counted[l.id] !== undefined && counted[l.id] !== "" ? Number(counted[l.id]) : null;
              const variance   = countedVal !== null ? countedVal - l.expectedQuantity : null;
              return (
                <tr key={l.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{l.item?.name}</div>
                    <div style={{ fontSize: 12, color: "#94a3b8" }}>{l.item?.code}</div>
                  </td>
                  <td style={{ fontWeight: 600 }}>{l.expectedQuantity}</td>
                  <td>
                    <input
                      className="input"
                      type="number"
                      min="0"
                      placeholder={String(l.expectedQuantity)}
                      value={counted[l.id] ?? ""}
                      onChange={(e) => setCounted(c => ({ ...c, [l.id]: e.target.value }))}
                      style={{ width: 110 }}
                    />
                  </td>
                  <td>
                    <select
                      className="input"
                      value={condition[l.id] || "GOOD"}
                      onChange={(e) => setCondition(c => ({ ...c, [l.id]: e.target.value }))}
                      style={{ width: 120 }}
                    >
                      <option value="GOOD">Good</option>
                      <option value="DAMAGED">Damaged</option>
                      <option value="SHORT">Short</option>
                    </select>
                  </td>
                  <td>
                    {variance === null ? (
                      <span style={{ color: "#94a3b8" }}>—</span>
                    ) : variance === 0 ? (
                      <span style={{ color: "#10b981", fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}><CheckCircle2 size={14}/> Match</span>
                    ) : (
                      <span style={{ color: variance < 0 ? "#dc2626" : "#f59e0b", fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>
                        <AlertTriangle size={14} />
                        {variance > 0 ? `+${variance}` : variance}
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <hr className="divider" />

        {error && (
          <div style={{ padding: "10px 14px", background: "#fee2e2", color: "#b91c1c", borderRadius: 6, marginBottom: 16, fontSize: 13 }}>
            {error}
          </div>
        )}

        <div className="confirm-footer">
          <div className="auto-note">
            <p className="auto-note-title">
              <CheckCircle size={16} className="auto-note-icon" />
              System will automatically:
            </p>
            <ul className="list">
              <li>Create the GRN and update inventory</li>
              <li>Add received quantity to available stock</li>
              <li>Freeze any variance and create a discrepancy record</li>
              <li>Record the receiving manager and timestamp</li>
            </ul>
          </div>
          <div className="actions-row">
            <button type="button" className="ch-btn ch-btn--outline" onClick={onCancel}>
              <X size={16} />
              Cancel
            </button>
            <button
              type="button"
              className="ch-btn ch-btn--success"
              onClick={onConfirm}
              disabled={!receipt || saving || confirmed}
              style={(!receipt || saving || confirmed) ? { opacity: 0.6, cursor: "not-allowed" } : undefined}
            >
              <CheckCircle size={16} />
              {saving ? "Confirming…" : confirmed ? "Confirmed" : "Confirm Receipt"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
