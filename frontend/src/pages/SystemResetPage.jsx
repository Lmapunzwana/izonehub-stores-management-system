import { useState } from "react";
import { AlertOctagon, Trash2 } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { apiFetch } from "../api";
import { useAppModal } from "../context/ModalContext";

const WIPED_ITEMS = [
  "All stores, all inventory levels, and all low-stock thresholds",
  "All items and all suppliers",
  "All projects and material requests (with dispatches, receipts and discrepancies)",
  "All GRNs, expected receipts, stock counts, stock adjustments and stock returns",
  "All batches/serial numbers, notifications and the audit log",
  "Every user account except System Administrators",
];

export default function SystemResetPage() {
  const { showAlert } = useAppModal();
  const [password, setPassword] = useState("");
  const [confirmationPhrase, setConfirmationPhrase] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);

  const canSubmit = password.length > 0 && confirmationPhrase === "RESET" && !submitting;

  async function handleReset(e) {
    e.preventDefault();
    if (!canSubmit) return;
    setError(null);
    setSubmitting(true);
    try {
      await apiFetch("/api/admin/reset", {
        method: "POST",
        body: { password, confirmationPhrase },
      });
      setDone(true);
      setPassword("");
      setConfirmationPhrase("");
      showAlert({
        title: "System reset complete",
        message: "The platform has been wiped to a clean slate. Only System Administrator accounts remain.",
        type: "success",
      });
    } catch (err) {
      setError(err.message || "Failed to reset the system.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card" style={{ maxWidth: 640, margin: "0 auto", border: "1px solid #fecaca" }}>
        <CardHeader title="System Reset" icon={<AlertOctagon size={20} color="#dc2626" />} />

        <div
          style={{
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: 10,
            padding: 16,
            marginBottom: 24,
          }}
        >
          <p style={{ margin: "0 0 10px 0", fontWeight: 700, color: "#991b1b" }}>
            This permanently deletes, with no undo:
          </p>
          <ul style={{ margin: 0, paddingLeft: 20, color: "#7f1d1d", lineHeight: 1.7 }}>
            {WIPED_ITEMS.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>

        {error && (
          <div style={{ padding: "12px 16px", background: "#fee2e2", color: "#b91c1c", borderRadius: 6, marginBottom: 20 }}>
            {error}
          </div>
        )}

        {done ? (
          <p style={{ color: "#166534", fontWeight: 600 }}>
            Reset complete. Refresh the app to see the clean state.
          </p>
        ) : (
          <form onSubmit={handleReset}>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: "block", marginBottom: 8, fontWeight: 500, color: "#475569" }}>
                Confirm your password
              </label>
              <input
                type="password"
                className="input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>

            <div style={{ marginBottom: 24 }}>
              <label style={{ display: "block", marginBottom: 8, fontWeight: 500, color: "#475569" }}>
                Type <strong>RESET</strong> to confirm
              </label>
              <input
                type="text"
                className="input"
                value={confirmationPhrase}
                onChange={(e) => setConfirmationPhrase(e.target.value)}
                placeholder="RESET"
              />
            </div>

            <button
              type="submit"
              className="btn"
              disabled={!canSubmit}
              style={{
                background: canSubmit ? "#dc2626" : "#fca5a5",
                color: "#fff",
                border: "none",
                padding: "10px 20px",
                borderRadius: 8,
                fontWeight: 600,
                display: "flex",
                alignItems: "center",
                gap: 8,
                cursor: canSubmit ? "pointer" : "not-allowed",
              }}
            >
              <Trash2 size={16} />
              {submitting ? "Wiping…" : "Wipe everything and reset"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
