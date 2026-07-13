import { History } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

// Real data from GET /api/audit-log (see backend/.../audit/AuditLog.java).
// Gated server-side to SYSTEM_ADMINISTRATOR/FINANCE/EXECUTIVE_MANAGEMENT —
// anyone else sees an empty list here, which is correct, not a bug.
export default function AuditLogPage() {
  const { auditLog } = useAppData();

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<History size={20} />}
          title="Audit Log"
          subtitle="Every recorded mutation across GRNs, dispatches, returns, discrepancies, and stock adjustments"
          badge={`${auditLog.length} events`}
        />
        <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
          {auditLog.map((entry, i) => (
            <div
              key={entry.id}
              style={{
                display: "flex",
                gap: 16,
                padding: "14px 0",
                borderBottom: i < auditLog.length - 1 ? "1px solid #f1f5f9" : "none",
              }}
            >
              <div
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: "50%",
                  background: "#dbeafe",
                  color: "#1e40af",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontWeight: 700,
                  fontSize: 11,
                  flexShrink: 0,
                  textAlign: "center",
                }}
              >
                {entry.entityType?.slice(0, 3) || "SYS"}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600 }}>
                  {entry.action} — {entry.description || `${entry.entityType} ${entry.entityId?.slice(0, 8)}`}
                </div>
                <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>{entry.performedBy}</div>
              </div>
              <div style={{ fontSize: 13, color: "#94a3b8", whiteSpace: "nowrap" }}>
                {entry.performedAt ? new Date(entry.performedAt).toLocaleString() : ""}
              </div>
            </div>
          ))}
          {auditLog.length === 0 && (
            <p style={{ color: "#64748b" }}>
              No audit events visible — either nothing has happened yet, or your role doesn't have access
              to this log.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
