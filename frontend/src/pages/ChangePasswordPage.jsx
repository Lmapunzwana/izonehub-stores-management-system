import { useState } from "react";
import { KeyRound, CheckCircle2, ShieldAlert } from "lucide-react";
import PageHeader from "../components/PageHeader";
import { apiFetch } from "../api";

export default function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSuccess(false);

    if (!currentPassword) {
      setError("Please enter your current password.");
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setError("New password must be at least 8 characters long.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("New passwords do not match.");
      return;
    }

    setBusy(true);
    try {
      await apiFetch("/api/auth/change-password", {
        method: "POST",
        body: { currentPassword, newPassword },
      });
      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setError(err?.message || "Failed to update password. Check your current password.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader title="Change Password" subtitle="Security & Credentials" />

      <div className="card" style={{ maxWidth: 540 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20 }}>
          <div style={{
            width: 40, height: 40, borderRadius: 10,
            background: "#dbeafe", color: "#1d4ed8",
            display: "flex", alignItems: "center", justifyContent: "center",
          }}>
            <KeyRound size={20} />
          </div>
          <div>
            <h2 className="card-title" style={{ margin: 0, fontSize: 18 }}>Update Your Password</h2>
            <p style={{ margin: "2px 0 0", fontSize: 13, color: "#64748b" }}>
              Ensure your account stays secure with a strong password.
            </p>
          </div>
        </div>

        {error && (
          <div style={{
            color: "#dc2626", background: "#fef2f2", border: "1px solid #fecaca",
            borderRadius: 8, padding: "10px 14px", marginBottom: 16, fontSize: 13,
            display: "flex", alignItems: "center", gap: 8,
          }}>
            <ShieldAlert size={16} />
            {error}
          </div>
        )}

        {success && (
          <div style={{
            color: "#15803d", background: "#f0fdf4", border: "1px solid #bbf7d0",
            borderRadius: 8, padding: "10px 14px", marginBottom: 16, fontSize: 13,
            display: "flex", alignItems: "center", gap: 8,
          }}>
            <CheckCircle2 size={16} />
            Password updated successfully!
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div>
            <label>Current Password</label>
            <input
              className="input"
              type="password"
              placeholder="Enter current password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </div>

          <div>
            <label>New Password</label>
            <input
              className="input"
              type="password"
              placeholder="At least 8 characters with digits & symbols"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>

          <div>
            <label>Confirm New Password</label>
            <input
              className="input"
              type="password"
              placeholder="Re-enter new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>

          <div className="actions-row" style={{ marginTop: 8 }}>
            <button
              type="submit"
              className="ch-btn ch-btn--primary"
              disabled={busy}
            >
              {busy ? "Updating Password…" : "Update Password"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
