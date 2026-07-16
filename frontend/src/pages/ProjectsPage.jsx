import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FolderPlus, ArrowRight, Lock } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

export default function ProjectsPage() {
  const navigate = useNavigate();
  const { projects, addProject, closeProject, stores, user } = useAppData();
  const { showConfirm } = useAppModal();

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");

  const canManage = isAdmin || isCentral;

  const openSiteStores = stores.filter(s => s.type === "SITE" && s.active);
  const [showForm, setShowForm] = useState(false);
  const [newProject, setNewProject] = useState({ code: "", name: "", budget: "", siteStoreId: "" });
  const [showClosed, setShowClosed] = useState(false);
  const [saving, setSaving] = useState(false);

  // Site managers only see projects for their assigned store
  const visibleProjects = (() => {
    const pool = isSite
      ? projects.filter(p => p.original?.siteStore?.id === user?.assignedStoreId)
      : projects;
    return pool.filter(p => showClosed || p.status === "Active");
  })();

  async function onSaveNewProject() {
    if (!newProject.code.trim() || !newProject.name.trim() || !newProject.siteStoreId) return;
    setSaving(true);
    try {
      await addProject({
        code:        newProject.code,
        name:        newProject.name,
        budget:      newProject.budget ? Number(newProject.budget) : undefined,
        siteStoreId: newProject.siteStoreId,
      });
      setNewProject({ code: "", name: "", budget: "", siteStoreId: "" });
      setShowForm(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Projects"
          actions={canManage ? [
            {
              label: showForm ? "Close" : "Create Project",
              icon: <FolderPlus size={16} />,
              variant: "primary",
              onClick: () => setShowForm(v => !v),
            },
          ] : []}
        />

        <div style={{ padding: "0 20px 10px 20px" }}>
          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer", fontSize: 14, color: "var(--text-color, #1e293b)" }}>
            <input
              type="checkbox"
              checked={showClosed}
              onChange={e => setShowClosed(e.target.checked)}
            />
            Show closed projects
          </label>
        </div>

        {showForm && canManage && (
          <div className="form-grid" style={{ marginBottom: 20 }}>
            <div>
              <label>Project Code</label>
              <input
                className="input"
                placeholder="RC-2026-015"
                value={newProject.code}
                onChange={e => setNewProject(f => ({ ...f, code: e.target.value }))}
              />
            </div>
            <div>
              <label>Project Name</label>
              <input
                className="input"
                placeholder="Project name"
                value={newProject.name}
                onChange={e => setNewProject(f => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div>
              <label>Site Store</label>
              <select
                className="input"
                value={newProject.siteStoreId}
                onChange={e => setNewProject(f => ({ ...f, siteStoreId: e.target.value }))}
              >
                <option value="">Select an open site store…</option>
                {openSiteStores.map(s => (
                  <option key={s.id} value={s.id}>{s.name} — {s.location}</option>
                ))}
              </select>
              {openSiteStores.length === 0 && (
                <p style={{ fontSize: 12, color: "#dc2626", marginTop: 4 }}>
                  No open site stores available — create one under Stores first.
                </p>
              )}
            </div>
            <div>
              <label>Budget Ceiling</label>
              <input
                className="input"
                type="number"
                placeholder="50000"
                value={newProject.budget}
                onChange={e => setNewProject(f => ({ ...f, budget: e.target.value }))}
              />
            </div>
            <div className="full actions-row">
              <button className="btn" onClick={() => setShowForm(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={onSaveNewProject}
                disabled={!newProject.siteStoreId || !newProject.code.trim() || !newProject.name.trim() || saving}
              >
                {saving ? "Saving…" : "Save Project"}
              </button>
            </div>
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Project Code</th>
              <th>Project Name</th>
              <th>Site Store</th>
              <th>Manager</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {visibleProjects.map(p => (
              <tr key={p.id}>
                <td style={{ fontWeight: 600, fontFamily: "monospace" }}>{p.code}</td>
                <td>{p.name}</td>
                <td>
                  {p.original.siteStore?.name}
                  {p.original.siteStore && !p.original.siteStore.active && (
                    <Badge type="default" style={{ marginLeft: 6 }}>
                      <Lock size={11} style={{ verticalAlign: "-1px", marginRight: 3 }} />
                      Closed
                    </Badge>
                  )}
                </td>
                <td>{p.manager || p.original?.siteStore?.manager?.fullName || "—"}</td>
                <td>
                  <Badge type={p.status === "Active" ? "success" : "default"}>{p.status}</Badge>
                </td>
                <td>
                  <div className="action-buttons">
                    <button
                      type="button"
                      className="ch-btn ch-btn--outline"
                      onClick={() => navigate(`/projects/${p.id}`)}
                    >
                      View Details
                      <ArrowRight size={16} />
                    </button>
                    {p.status === "Active" && canManage && (
                      <button
                        type="button"
                        className="ch-btn ch-btn--danger"
                        onClick={() => {
                          showConfirm({
                            title: "Close Project",
                            message: `Close "${p.name}"? Its site store will enter pending shutdown if no other active project uses it.`,
                            type: "warning",
                            confirmText: "Yes, Close",
                            onConfirm: () => closeProject(p.id),
                          });
                        }}
                      >
                        Close Project
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {visibleProjects.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  {isSite ? "No projects assigned to your store." : "No projects yet."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
