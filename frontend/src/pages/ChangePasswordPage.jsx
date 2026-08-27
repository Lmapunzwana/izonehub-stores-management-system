import { useState } from "react";
import { KeyRound, CheckCircle2, ShieldAlert, Check, X } from "lucide-react";
import PageHeader from "../components/PageHeader";
import { apiFetch } from "../api";

const PASSWORD_REQUIREMENTS = [
  { id: "length",  label: "At least 8 characters", test: (p) => p.length >= 8 },
  { id: "upper",   label: "At least 1 uppercase letter (A-Z)", test: (p) => /[A-Z]/.test(p) },
  { id: "number",  label: "At least 1 number (0-9)", test: (p) => /[0-9]/.test(p) },
  { id: "special", label: "At least 1 special character (!@#$%^&*)", test: (p) => /[^A-Za-z0-9]/.test(p) },
];

export default function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordTouched, setPasswordTouched] = useState(false);
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
              placeholder="e.g. NewPass123!"
              value={newPassword}
              onFocus={() => setPasswordTouched(true)}
              onBlur={() => setPasswordTouched(true)}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <div style={{ marginTop: 8, display: "flex", flexDirection: "column", gap: 4, fontSize: 12 }}>
              {PASSWORD_REQUIREMENTS.map((req) => {
                const pass = req.test(newPassword);
                const active = passwordTouched || newPassword.length > 0;
                const color = !active ? "#64748b" : pass ? "#16a34a" : "#dc2626";
                return (
                  <div key={req.id} style={{ display: "flex", alignItems: "center", gap: 6, color, fontWeight: active && !pass ? 600 : 400 }}>
                    {active ? (
                      pass ? <Check size={14} color="#16a34a" /> : <X size={14} color="#dc2626" />
                    ) : (
                      <span style={{ width: 14, textAlign: "center", opacity: 0.5 }}>•</span>
                    )}
                    {req.label}
                  </div>
                );
              })}
            </div>
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
