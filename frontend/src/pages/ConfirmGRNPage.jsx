import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle, X } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

// POST /api/expected-receipts/{id}/confirm takes optional per-line overrides
// { lines: [{ lineId, receivedQuantity, condition }] }. Any line NOT listed
// defaults to "received in full, GOOD condition" on the backend, so an empty
// counted field is a valid no-op override, not a missing value.
export default function ConfirmGRNPage() {
  const navigate = useNavigate();
  const { expectedReceipts, confirmGRN } = useAppData();
  const pending = expectedReceipts.filter((r) => r.statusIndex >= 1 && r.statusIndex < 2);
  const [receiptId, setReceiptId] = useState(pending[0]?.id || "");
  const [counted, setCounted] = useState({}); // lineId -> value
  const [condition, setCondition] = useState({}); // lineId -> GOOD/DAMAGED/SHORT
  const [confirmed, setConfirmed] = useState(false);

  const receipt = expectedReceipts.find((r) => r.id === receiptId);
  const lines = receipt?.original?.lines || [];

  useEffect(() => {
    if (!receiptId && pending.length) setReceiptId(pending[0].id);
  }, [pending, receiptId]);

  function onCancel() {
    navigate("/expected-receipts");
  }

  async function onConfirm() {
    if (!receipt) return;
    const overrides = lines
      .filter((l) => counted[l.id] !== undefined && counted[l.id] !== "")
      .map((l) => ({
        lineId: l.id,
        receivedQuantity: Number(counted[l.id]),
        condition: condition[l.id] || "GOOD",
      }));
    try {
      await confirmGRN(receipt.id, overrides);
      setConfirmed(true);
      setTimeout(() => navigate("/items"), 700);
    } catch {
      // confirmGRN already logs the error; keep the user on the page.
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
              : { label: "Awaiting GRN", variant: "warning" }
          }
        />

        <div className="form-grid">
          <div>
            <label>Expected Receipt</label>
            <select
              className="input"
              value={receiptId}
              onChange={(e) => {
                setReceiptId(e.target.value);
                setCounted({});
                setCondition({});
              }}
            >
              {pending.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.receiptNo} — {r.supplier}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Supplier</label>
            <input className="input" value={receipt?.supplier || ""} disabled />
          </div>
        </div>

        <hr className="divider" />

        <h2 className="card-title">Physical Count</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Expected</th>
              <th>Counted</th>
              <th>Condition</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => (
              <tr key={l.id}>
                <td>{l.item?.name}</td>
                <td>{l.expectedQuantity}</td>
                <td>
                  <input
                    className="input"
                    type="number"
                    placeholder={String(l.expectedQuantity)}
                    value={counted[l.id] ?? ""}
                    onChange={(e) =>
                      setCounted((c) => ({ ...c, [l.id]: e.target.value }))
                    }
                  />
                </td>
                <td>
                  <select
                    className="input"
                    value={condition[l.id] || "GOOD"}
                    onChange={(e) =>
                      setCondition((c) => ({ ...c, [l.id]: e.target.value }))
                    }
                  >
                    <option value="GOOD">Good</option>
                    <option value="DAMAGED">Damaged</option>
                    <option value="SHORT">Short</option>
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <hr className="divider" />

        <div className="confirm-footer">
          <div className="auto-note">
            <p className="auto-note-title">
              <CheckCircle size={16} className="auto-note-icon" />
              System will automatically:
            </p>
            <ul className="list">
              <li>Create the GRN</li>
              <li>Add counted stock to inventory</li>
              <li>Record the receiving manager</li>
              <li>Open a discrepancy if any line's counted quantity or condition differs from expected</li>
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
              disabled={!receipt}
              style={!receipt ? { opacity: 0.5, cursor: "not-allowed" } : undefined}
            >
              <CheckCircle size={16} />
              Confirm Receipt
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
