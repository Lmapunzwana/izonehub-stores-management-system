import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FolderPlus, ArrowRight, Lock } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

export default function ProjectsPage() {
  const navigate = useNavigate();
  const { projects, addProject, closeProject, stores } = useAppData();
  const { showConfirm } = useAppModal();
  const [showForm, setShowForm] = useState(false);
  const openSiteStores = stores.filter((s) => s.type === "SITE" && s.active);
  const [newProject, setNewProject] = useState({ code: "", name: "", budget: "", siteStoreId: "" });

  async function onSaveNewProject() {
    if (!newProject.code.trim() || !newProject.name.trim() || !newProject.siteStoreId) return;
    await addProject({
      code: newProject.code,
      name: newProject.name,
      budget: newProject.budget ? Number(newProject.budget) : undefined,
      siteStoreId: newProject.siteStoreId,
    });
    setNewProject({ code: "", name: "", budget: "", siteStoreId: "" });
    setShowForm(false);
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Projects"
          actions={[
            {
              label: showForm ? "Close" : "Create Project",
              icon: <FolderPlus size={16} />,
              variant: "primary",
              onClick: () => setShowForm((v) => !v),
            },
          ]}
        />

        {showForm && (
          <div className="form-grid" style={{ marginBottom: 20 }}>
            <div>
              <label>Project Code</label>
              <input
                className="input"
                placeholder="RC-2026-015"
                value={newProject.code}
                onChange={(e) => setNewProject((f) => ({ ...f, code: e.target.value }))}
              />
            </div>
            <div>
              <label>Project Name</label>
              <input
                className="input"
                placeholder="Project name"
                value={newProject.name}
                onChange={(e) => setNewProject((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div>
              <label>Site Store</label>
              <select
                className="input"
                value={newProject.siteStoreId}
                onChange={(e) => setNewProject((f) => ({ ...f, siteStoreId: e.target.value }))}
              >
                <option value="">Select an open site store…</option>
                {openSiteStores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} — {s.location}
                  </option>
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
                onChange={(e) => setNewProject((f) => ({ ...f, budget: e.target.value }))}
              />
            </div>
            <div className="full actions-row">
              <button className="btn" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={onSaveNewProject} disabled={!newProject.siteStoreId}>
                Save Project
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
            {projects.map((p) => (
              <tr key={p.id}>
                <td>{p.code}</td>
                <td>{p.name}</td>
                <td>
                  {p.original.siteStore?.name}
                  {p.original.siteStore && !p.original.siteStore.active && (
                    <Badge type="default">
                      <Lock size={11} style={{ verticalAlign: "-1px", marginRight: 3 }} />
                      Closed
                    </Badge>
                  )}
                </td>
                <td>{p.manager}</td>
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
                    {p.status === "Active" && (
                      <button
                        type="button"
                        className="ch-btn ch-btn--danger"
                        onClick={() => {
                          showConfirm({
                            title: "Close Project",
                            message: `Close "${p.name}"? Its site store will close too if no other active project uses it.`,
                            type: "warning",
                            confirmText: "Yes, Close",
                            onConfirm: () => closeProject(p.id)
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
            {projects.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No projects yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
