import { useState } from "react";
import PageHeader from "../components/PageHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { apiFetch } from "../api";

// backend/.../user/UserCommandService.java explicitly rejects creating a
// user with the SYSTEM_ADMINISTRATOR role through this endpoint ("System
// Administrator accounts cannot be created through application user
// management") — so it's deliberately excluded here, not an oversight.
const ASSIGNABLE_ROLES = [
  "CENTRAL_STORE_MANAGER",
  "SITE_STORE_MANAGER",
];

const ROLE_LABEL = {
  CENTRAL_STORE_MANAGER: "Central Store Manager",
  SITE_STORE_MANAGER: "Site Store Manager",
  SYSTEM_ADMINISTRATOR: "System Administrator",
};

const EMPTY_FORM = { fullName: "", email: "", temporaryPassword: "", roles: [] };

export default function UsersPage() {
  const { users, stores, addUser, refreshItems } = useAppData();
  const [userList, setUserList] = useState(users);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [creating, setCreating] = useState(false);

  function toggleRole(role) {
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role],
    }));
  }

  async function onCreateUser() {
    setError(null);
    if (!form.fullName.trim() || !form.email.trim() || !form.temporaryPassword.trim() || form.roles.length === 0) {
      setError("Full name, email, temporary password, and at least one role are required.");
      return;
    }
    setCreating(true);
    try {
      await addUser(form);
      const updated = await apiFetch("/api/users");
      setUserList(
        (Array.isArray(updated) ? updated : updated.content || []).map((u) => ({
          id: u.id,
          fullName: u.fullName,
          email: u.email,
          roles: u.roles || [],
          store: u.assignedStore?.name || "Unassigned",
          active: u.active,
          locked: u.locked,
        }))
      );
      setForm(EMPTY_FORM);
    } catch (e) {
      setError(e?.message || "Could not create user — check the password meets policy and the email isn't already in use.");
    } finally {
      setCreating(false);
    }
  }

  async function onUnlock(id) {
    setBusyId(id);
    try {
      await apiFetch(`/api/users/${id}/unlock`, { method: "POST" });
      setUserList((rows) => rows.map((u) => (u.id === id ? { ...u, locked: false } : u)));
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  }

  async function onDeactivate(id) {
    setBusyId(id);
    try {
      await apiFetch(`/api/users/${id}/deactivate`, { method: "POST" });
      setUserList((rows) => rows.map((u) => (u.id === id ? { ...u, active: false } : u)));
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  }

  async function onAssignStore(userId, storeId) {
    if (!storeId) return;
    setBusyId(userId);
    try {
      await apiFetch(`/api/users/${userId}/store/${storeId}`, { method: "PUT" });
      const updated = await apiFetch("/api/users");
      setUserList(
        (Array.isArray(updated) ? updated : updated.content || []).map((u) => ({
          id: u.id,
          fullName: u.fullName,
          email: u.email,
          roles: u.roles || [],
          store: u.assignedStore?.name || "Unassigned",
          active: u.active,
          locked: u.locked,
        }))
      );
    } catch (e) {
      console.error(e);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page">
      <PageHeader title="Users" subtitle="System administration" />

      <div className="card">
        <h2 className="card-title">Create User</h2>

        {error && (
          <p style={{ color: "#dc2626", fontSize: 13, marginTop: -8, marginBottom: 12 }}>{error}</p>
        )}

        <div className="form-grid">
          <div>
            <label>Full Name</label>
            <input
              className="input"
              placeholder="John Doe"
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
            />
          </div>

          <div>
            <label>Email</label>
            <input
              className="input"
              placeholder="john@example.com"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            />
          </div>

          <div>
            <label>Temporary Password</label>
            <input
              className="input"
              type="password"
              placeholder="Meets your org's password policy"
              value={form.temporaryPassword}
              onChange={(e) => setForm((f) => ({ ...f, temporaryPassword: e.target.value }))}
            />
          </div>

          <div>
            <label>Roles</label>
            <div className="checkbox-group">
              {ASSIGNABLE_ROLES.map((role) => (
                <label key={role}>
                  <input
                    type="checkbox"
                    checked={form.roles.includes(role)}
                    onChange={() => toggleRole(role)}
                  />{" "}
                  {ROLE_LABEL[role]}
                </label>
              ))}
            </div>
          </div>

          <div className="full actions-row">
            <button className="btn" onClick={() => setForm(EMPTY_FORM)} disabled={creating}>
              Clear
            </button>
            <button className="btn btn-primary" onClick={onCreateUser} disabled={creating}>
              {creating ? "Creating…" : "Create User"}
            </button>
          </div>
        </div>
      </div>

      <div className="card">
        <h2 className="card-title">Existing Users</h2>

        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Store</th>
              <th>Roles</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {userList.map((u) => (
              <tr key={u.id}>
                <td>{u.fullName}</td>
                <td>
                  {u.store === "Unassigned" ? (
                    <select
                      className="input"
                      style={{ padding: "4px 8px", fontSize: "12px", width: "160px" }}
                      disabled={busyId === u.id}
                      onChange={(e) => onAssignStore(u.id, e.target.value)}
                      value=""
                    >
                      <option value="" disabled>Assign Store...</option>
                      {stores.map((s) => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                      ))}
                    </select>
                  ) : (
                    u.store
                  )}
                </td>
                <td>
                  {u.roles.length ? (
                    u.roles.map((role) => (
                      <Badge key={role} type={role === "CENTRAL_STORE_MANAGER" ? "success" : "info"}>
                        {ROLE_LABEL[role] || role}
                      </Badge>
                    ))
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>No roles</span>
                  )}
                </td>
                <td>
                  <Badge type={!u.active ? "danger" : u.locked ? "warning" : "success"}>
                    {!u.active ? "Deactivated" : u.locked ? "Locked" : "Active"}
                  </Badge>
                </td>
                <td>
                  <div className="action-buttons">
                    {u.locked && (
                      <button className="btn" disabled={busyId === u.id} onClick={() => onUnlock(u.id)}>
                        {busyId === u.id ? "Unlocking…" : "Unlock"}
                      </button>
                    )}
                    {u.active && (
                      <button
                        className="btn btn-danger"
                        disabled={busyId === u.id}
                        onClick={() => onDeactivate(u.id)}
                      >
                        {busyId === u.id ? "Deactivating…" : "Deactivate"}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {userList.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", color: "#64748b" }}>
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
