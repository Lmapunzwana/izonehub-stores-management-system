import { useState } from "react";
import { Send, PackageCheck, FileText } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

const STATUS_TYPE = {
  Approved:  "info",
  "In Transit": "warning",
  Received:  "success",
  "Received (Discrepancy)": "danger",
};

export default function DispatchPage() {
  const { materialRequests, dispatchRequest, markRequestReceived, user } = useAppData();
  const { showAlert } = useAppModal();
  const [collector, setCollector] = useState({});
  const [busyId, setBusyId] = useState(null);

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");

  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function downloadMIV(requestId) {
    try {
      const blob = await apiFetch(`/api/material-requests/${requestId}/dispatch-note`);
      downloadBlob(blob, `dispatch-note-${requestId}.pdf`);
    } catch (e) {
      showAlert({ title: "PDF Unavailable", message: "Could not download MIV document. " + (e?.message || ""), type: "warning" });
    }
  }

  async function onDispatch(r) {
    const name = collector[r.id]?.name || "";
    if (!name.trim()) {
      showAlert({ title: "Collector Required", message: "Please enter the collector's name before dispatching.", type: "warning" });
      return;
    }
    setBusyId(r.id);
    try {
      await dispatchRequest(r.id, name, collector[r.id]?.empId);
      // PDF download immediately after successful dispatch
      await downloadMIV(r.id);
    } catch (e) {
      showAlert({ title: "Dispatch Failed", message: e?.message || "Failed to dispatch.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  async function onMarkReceived(r) {
    setBusyId(r.id);
    try {
      await markRequestReceived(r.id);
    } catch (e) {
      showAlert({ title: "Error", message: e?.message || "Failed to mark as received.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  function linesSummary(r) {
    return r.lines.map(l => `${l.item} × ${l.requested}`).join(", ") || "—";
  }

  // Central/Admin: show approved items to dispatch
  // Site Manager: show approved (coming soon), in-transit, and received items to track full lifecycle
  const centralItems = materialRequests.filter(r => r.status === "Approved" || r.status === "In Transit" || r.status === "Received" || r.status === "Received (Discrepancy)");
  const siteItems    = materialRequests.filter(r =>
    r.status === "Approved" ||
    r.status === "In Transit" ||
    r.status === "Received" ||
    r.status === "Received (Discrepancy)"
  );

  const rows = (isCentral || isAdmin) ? centralItems : siteItems;

  const awaitingDispatch = materialRequests.filter(r => r.status === "Approved").length;

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<Send size={20} />}
          title="Issues & Dispatch"
          subtitle={
            (isCentral || isAdmin)
              ? "Create MIVs, dispatch approved requests, and download dispatch notes"
              : "Track your material requests — from approval through dispatch, receipt, and beyond"
          }
          status={{
            label: (isCentral || isAdmin)
              ? `${awaitingDispatch} awaiting dispatch`
              : `${siteItems.filter(r => r.status === "In Transit").length} in transit`,
            variant: awaitingDispatch > 0 || siteItems.filter(r => r.status === "In Transit").length > 0 ? "warning" : "success",
          }}
        />

        <table className="table">
          <thead>
            <tr>
              <th>Request No</th>
              <th>Project</th>
              <th>Items</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.requestNo}>
                <td style={{ fontWeight: 600 }}>{r.requestNo}</td>
                <td>{r.project}</td>
                <td style={{ fontSize: 13, color: "#64748b" }}>{linesSummary(r)}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                </td>
                <td>
                  {/* Central Manager: create MIV & dispatch for approved */}
                  {r.status === "Approved" && (isCentral || isAdmin) && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <input
                        className="input"
                        placeholder="Collector name *"
                        style={{ width: 150 }}
                        value={collector[r.id]?.name || ""}
                        onChange={e => setCollector(c => ({ ...c, [r.id]: { ...c[r.id], name: e.target.value } }))}
                      />
                      <button
                        className="ch-btn ch-btn--primary"
                        disabled={busyId === r.id}
                        onClick={() => onDispatch(r)}
                      >
                        <Send size={16} />
                        {busyId === r.id ? "Creating…" : "Create MIV & Dispatch"}
                      </button>
                    </div>
                  )}

                  {/* Central Manager: in-transit — download MIV */}
                  {r.status === "In Transit" && (isCentral || isAdmin) && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span style={{ color: "#64748b", fontSize: 13 }}>Awaiting site receipt</span>
                      <button className="ch-btn ch-btn--outline" style={{ padding: "4px 10px", fontSize: 13 }} onClick={() => downloadMIV(r.id)}>
                        <FileText size={14} /> MIV PDF
                      </button>
                    </div>
                  )}

                  {/* Site Manager: mark received */}
                  {r.status === "In Transit" && isSite && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <button
                        className="ch-btn ch-btn--success"
                        disabled={busyId === r.id}
                        onClick={() => onMarkReceived(r)}
                      >
                        <PackageCheck size={16} />
                        {busyId === r.id ? "Processing…" : "Mark Received"}
                      </button>
                      <button className="ch-btn ch-btn--outline" style={{ padding: "4px 10px", fontSize: 13 }} onClick={() => downloadMIV(r.id)}>
                        <FileText size={14} /> MIV PDF
                      </button>
                    </div>
                  )}

                  {/* Completed rows: MIV PDF only */}
                  {(r.status === "Received" || r.status === "Received (Discrepancy)") && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span style={{ color: "#64748b", fontSize: 13 }}>Complete</span>
                      <button className="ch-btn ch-btn--outline" style={{ padding: "4px 10px", fontSize: 13 }} onClick={() => downloadMIV(r.id)}>
                        <FileText size={14} /> MIV PDF
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}

            {rows.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", color: "#64748b", padding: "24px 0" }}>
                  {(isCentral || isAdmin)
                    ? "Nothing to dispatch yet — approve a material request first."
                    : "No material requests found for your site store yet."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
