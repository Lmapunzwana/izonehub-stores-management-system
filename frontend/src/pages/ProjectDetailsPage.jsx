import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  FolderOpen, MapPin, Calendar, Send, Users, UserPlus, DollarSign, X,
} from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

const TABS = [
  { key: "all",      label: "All Requests" },
  { key: "pending",  label: "Pending" },
  { key: "approved", label: "Approved" },
  { key: "issued",   label: "Issued" },
  { key: "rejected", label: "Rejected" },
];

const STATUS_TYPE = {
  "Pending Approval": "warning",
  Approved:           "info",
  "In Transit":       "warning",
  Received:           "success",
  "Received (Discrepancy)": "danger",
  Rejected:           "danger",
};

export default function ProjectDetailsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { projects, materialRequests, users, assignEmployeeToProject, removeEmployeeFromProject, user } = useAppData();
  const [showPicker, setShowPicker] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState("");
  const [activeTab, setActiveTab] = useState("all");

  const canManage = user?.roles?.includes("SYSTEM_ADMINISTRATOR") || user?.roles?.includes("CENTRAL_STORE_MANAGER");

  const project = projects.find(p => p.id === id);

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

  const projectRequests = materialRequests.filter(r => r.original.project?.id === project.id);

  function filterByTab(tab) {
    switch (tab) {
      case "pending":  return projectRequests.filter(r => r.status === "Pending Approval");
      case "approved": return projectRequests.filter(r => r.status === "Approved");
      case "issued":   return projectRequests.filter(r => r.status === "In Transit" || r.status === "Received" || r.status === "Received (Discrepancy)");
      case "rejected": return projectRequests.filter(r => r.status === "Rejected");
      default:         return projectRequests;
    }
  }

  const lineRows = filterByTab(activeTab).flatMap(r =>
    r.lines.map(l => ({ ...l, requestNo: r.requestNo, status: r.status, requestId: r.id }))
  );

  // Employee picker: filter by SITE_STORE_MANAGER role, exclude already assigned
  const assignedEmployeeIds = new Set(
    (project.original?.assignedEmployees || []).map(e => e.id)
  );
  const eligibleUsers = users.filter(u =>
    u.active &&
    (u.roles.includes("SITE_STORE_MANAGER") || u.roles.includes("SITE_STORE_USER")) &&
    !assignedEmployeeIds.has(u.id)
  );

  return (
    <div className="page">
      {/* Project Info Header */}
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

      {/* Material Requests with Tabs */}
      <div className="card">
        <CardHeader
          title="Material Requests"
          actions={[
            {
              label: "New Request",
              icon: <Send size={16} />,
              variant: "outline",
              onClick: () => navigate("/material-requests/add-item"),
            },
          ]}
        />

        {/* Tabs */}
        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #e5e7eb", paddingBottom: 0, marginBottom: 16, overflowX: "auto" }}>
          {TABS.map(tab => {
            const count = filterByTab(tab.key).length;
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                style={{
                  padding: "8px 16px",
                  border: "none",
                  background: "none",
                  cursor: "pointer",
                  fontWeight: activeTab === tab.key ? 700 : 400,
                  color: activeTab === tab.key ? "#2563eb" : "#64748b",
                  borderBottom: activeTab === tab.key ? "2px solid #2563eb" : "2px solid transparent",
                  fontSize: 14,
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  whiteSpace: "nowrap",
                }}
              >
                {tab.label}
                {count > 0 && (
                  <span style={{
                    background: activeTab === tab.key ? "#dbeafe" : "#f1f5f9",
                    color: activeTab === tab.key ? "#1d4ed8" : "#64748b",
                    borderRadius: 12,
                    padding: "1px 7px",
                    fontSize: 12,
                    fontWeight: 600,
                  }}>
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

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
                <td style={{ fontFamily: "monospace", fontWeight: 600, fontSize: 13 }}>{l.requestNo}</td>
                <td>{l.item}</td>
                <td>{l.requested}</td>
                <td>{l.dispatched ?? "—"}</td>
                <td>{l.received ?? "—"}</td>
                <td>
                  <Badge type={STATUS_TYPE[l.status] || "default"}>{l.status}</Badge>
                </td>
              </tr>
            ))}
            {lineRows.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No {activeTab !== "all" ? activeTab + " " : ""}material requests for this project.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Assigned Employees */}
      <div className="card">
        <CardHeader
          icon={<Users size={20} />}
          title="Assigned Employees"
          actions={canManage ? [
            {
              label: showPicker ? "Close" : "Assign Employee",
              icon: <UserPlus size={16} />,
              variant: "primary",
              onClick: () => setShowPicker(v => !v),
            },
          ] : []}
        />

        {showPicker && canManage && (
          <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
            <select
              className="input"
              value={selectedUserId}
              onChange={e => setSelectedUserId(e.target.value)}
              style={{ flex: 1 }}
            >
              <option value="">Select a user…</option>
              {eligibleUsers.map(u => (
                <option key={u.id} value={u.id}>
                  {u.fullName} ({u.store || "No store"})
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
          {(project.original.assignedEmployees || []).map(e => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid #f1f5f9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{
                  width: 36, height: 36, borderRadius: "50%", background: "#2563eb",
                  color: "#fff", display: "flex", alignItems: "center", justifyContent: "center",
                  fontWeight: 700, fontSize: 14, flexShrink: 0,
                }}>
                  {e.fullName?.[0] || "?"}
                </div>
                <div>
                  <div style={{ fontWeight: 600 }}>{e.fullName}</div>
                  <div style={{ fontSize: 12, color: "#64748b" }}>{(e.roles || [])[0] || "—"}</div>
                </div>
              </div>
              {canManage && (
                <button
                  className="ch-btn ch-btn--ghost"
                  onClick={() => removeEmployeeFromProject(project.id, e.id)}
                  title="Remove from this project"
                >
                  <X size={14} />
                </button>
              )}
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
