import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  FolderOpen, MapPin, Calendar, Send, Users, UserPlus, DollarSign, X,
} from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

export default function ProjectDetailsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { projects, materialRequests, users, assignEmployeeToProject, removeEmployeeFromProject } = useAppData();
  const [showPicker, setShowPicker] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState("");

  const project = projects.find((p) => p.id === id);

  if (!project) {
    return (
      <div className="page">
        <div className="card">
          <p>No project found for ID "{id}".</p>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate("/projects")}>
            Back to Projects
          </button>
        </div>
      </div>
    );
  }

  // There is no project-scoped consumption/audit-trail endpoint on the
  // backend yet, so "material summary" and "activity" are derived here from
  // the material requests already tied to this project via their .project
  // relation — not fabricated numbers.
  const projectRequests = materialRequests.filter((r) => r.original.project?.id === project.id);
  const lineRows = projectRequests.flatMap((r) =>
    r.lines.map((l) => ({ ...l, requestNo: r.requestNo, status: r.status }))
  );

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<FolderOpen size={20} />}
          title={project.name}
          subtitle={`Project Code: ${project.code}`}
          status={{ label: project.status, variant: project.status === "Active" ? "success" : "default" }}
        />
        <div className="detail-grid">
          <div className="detail-item">
            <div className="detail-label">
              <MapPin size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />
              Site Store
            </div>
            <div className="detail-value">{project.original.siteStore?.name || "—"}</div>
          </div>
          <div className="detail-item">
            <div className="detail-label">
              <DollarSign size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />
              Budget Ceiling
            </div>
            <div className="detail-value">
              {project.budget != null ? `$${Number(project.budget).toLocaleString()}` : "—"}
            </div>
          </div>
          <div className="detail-item">
            <div className="detail-label">
              <Calendar size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />
              Created
            </div>
            <div className="detail-value">
              {project.original.createdAt ? project.original.createdAt.slice(0, 10) : "—"}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <CardHeader
          title="Material Requests for this Project"
          actions={[
            {
              label: "Issue Materials",
              icon: <Send size={16} />,
              variant: "outline",
              onClick: () => navigate("/material-requests/add-item"),
            },
          ]}
        />
        <table className="table">
          <thead>
            <tr>
              <th>Request No</th>
              <th>Item</th>
              <th>Requested</th>
              <th>Dispatched</th>
              <th>Received</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {lineRows.map((l, i) => (
              <tr key={`${l.requestNo}-${i}`}>
                <td>{l.requestNo}</td>
                <td>{l.item}</td>
                <td>{l.requested}</td>
                <td>{l.dispatched}</td>
                <td>{l.received}</td>
                <td>
                  <Badge type="default">{l.status}</Badge>
                </td>
              </tr>
            ))}
            {lineRows.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No material requests linked to this project yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card">
        <CardHeader
          icon={<Users size={20} />}
          title="Assigned Employees"
          actions={[
            {
              label: showPicker ? "Close" : "Assign Employee",
              icon: <UserPlus size={16} />,
              variant: "primary",
              onClick: () => setShowPicker((v) => !v),
            },
          ]}
        />

        {showPicker && (
          <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
            <select
              className="input"
              value={selectedUserId}
              onChange={(e) => setSelectedUserId(e.target.value)}
              style={{ flex: 1 }}
            >
              <option value="">Select a user…</option>
              {users
                .filter((u) => !(project.original.assignedEmployees || []).some((e) => e.id === u.id))
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} ({u.store})
                  </option>
                ))}
            </select>
            <button
              className="ch-btn ch-btn--primary"
              disabled={!selectedUserId}
              onClick={async () => {
                await assignEmployeeToProject(project.id, selectedUserId);
                setSelectedUserId("");
                setShowPicker(false);
              }}
            >
              Assign
            </button>
          </div>
        )}

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          {(project.original.assignedEmployees || []).map((e) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div
                  style={{
                    width: 36, height: 36, borderRadius: "50%", background: "#2563eb",
                    color: "#fff", display: "flex", alignItems: "center", justifyContent: "center",
                    fontWeight: 700, fontSize: 14, flexShrink: 0,
                  }}
                >
                  {e.fullName?.[0] || "?"}
                </div>
                <div style={{ fontWeight: 700 }}>{e.fullName}</div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span className="badge info">{(e.roles || [])[0] || "—"}</span>
                <button
                  className="ch-btn ch-btn--ghost"
                  onClick={() => removeEmployeeFromProject(project.id, e.id)}
                  title="Remove from this project"
                >
                  <X size={14} />
                </button>
              </div>
            </div>
          ))}
          {(project.original.assignedEmployees || []).length === 0 && (
            <p style={{ color: "#64748b" }}>No employees assigned to this project yet.</p>
          )}
        </div>
      </div>
    </div>
  );
}
