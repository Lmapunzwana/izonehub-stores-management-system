import { useState, useEffect } from "react";
import { X, CheckCircle2 } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function ConfirmReturnModal({ isOpen, onClose, returnRecord, onSuccess }) {
  const { showAlert } = useAppModal();
  const [lines, setLines] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen && returnRecord) {
      setLines(returnRecord.original.lines.map(l => ({
        itemId: l.item.id,
        itemName: l.item.name,
        expectedQuantity: l.quantity,
        receivedQuantity: l.quantity // default to expected
      })));
    }
  }, [isOpen, returnRecord]);

  if (!isOpen || !returnRecord) return null;

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await apiFetch(`/api/returns/${returnRecord.id}/confirm`, {
        method: "POST",
        body: {
          lines: lines.map(l => ({
            itemId: l.itemId,
            receivedQuantity: Number(l.receivedQuantity)
          }))
        }
      });
      onSuccess();
      onClose();
    } catch (e) {
      console.error(e);
      alert("Failed to confirm return: " + e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh", overflowY: "auto" }}>
      <div className="app-modal" style={{ maxWidth: 600, padding: 24, textAlign: "left" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h2 style={{ margin: 0, fontSize: "1.25rem" }}>Confirm Return {returnRecord.returnNo}</h2>
          <button type="button" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#64748b" }}>
            <X size={20} />
          </button>
        </div>

        <p style={{ color: "#64748b", marginBottom: 20 }}>
          Enter the actual physical quantities received at the Central Store. Any shortages will automatically create a Discrepancy record.
        </p>

        <table className="table" style={{ marginBottom: 20 }}>
          <thead>
            <tr>
              <th>Item</th>
              <th>Expected</th>
              <th>Received</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={l.itemId}>
                <td>{l.itemName}</td>
                <td>{l.expectedQuantity}</td>
                <td>
                  <input
                    type="number"
                    className="input"
                    style={{ width: 100 }}
                    min="0"
                    max={l.expectedQuantity}
                    value={l.receivedQuantity}
                    onChange={(e) => {
                      const newLines = [...lines];
                      newLines[i].receivedQuantity = e.target.value;
                      setLines(newLines);
                    }}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 12 }}>
          <button className="btn btn-outline" onClick={onClose} disabled={submitting}>Cancel</button>
          <button className="btn btn-success" onClick={handleSubmit} disabled={submitting}>
            <CheckCircle2 size={16} /> Confirm Receipt
          </button>
        </div>
      </div>
    </div>
  );
}
