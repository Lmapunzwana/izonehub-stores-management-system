import { useState, Fragment } from "react";
import { Send, PackageCheck, FileText, ChevronDown, ChevronUp, ArrowLeftRight } from "lucide-react";
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
  "Pending Central Approval": "warning",
};

export default function DispatchPage() {
  const { materialRequests, dispatchRequest, markRequestReceived, centralApproveRequest, rejectRequest, user } = useAppData();
  const { showAlert, showConfirm } = useAppModal();
  const [collector, setCollector] = useState({});
  const [busyId, setBusyId] = useState(null);
  const [expandedId, setExpandedId] = useState(null);

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

  async function onCentralApprove(r) {
    setBusyId(r.id);
    try {
      await centralApproveRequest(r.id);
      showAlert({ title: "Approved", message: `${r.sourceStore} → ${r.requestingStore} can now be dispatched.`, type: "success" });
    } catch (e) {
      showAlert({ title: "Error", message: e?.message || "Failed to approve.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  function onCentralReject(r) {
    showConfirm({
      title: "Reject Transfer?",
      message: `Reject the ${r.sourceStore} → ${r.requestingStore} transfer? Reserved stock at ${r.sourceStore} will be released.`,
      type: "danger",
      confirmText: "Reject",
      onConfirm: async () => {
        setBusyId(r.id);
        try {
          await rejectRequest(r.id, "Rejected by Central Store");
        } catch (e) {
          showAlert({ title: "Error", message: e?.message || "Failed to reject.", type: "danger" });
        } finally {
          setBusyId(null);
        }
      },
    });
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

  // Central/Admin: show items awaiting their central approval, ready to dispatch, or already moving
  // Site Manager: show approved (coming soon), in-transit, and received items to track full lifecycle
  const centralItems = materialRequests.filter(r =>
    r.status === "Pending Central Approval" ||
    r.status === "Approved" || r.status === "In Transit" || r.status === "Received" || r.status === "Received (Discrepancy)"
  );
  const siteItems    = materialRequests.filter(r =>
    r.status === "Pending Central Approval" ||
    r.status === "Approved" ||
    r.status === "In Transit" ||
    r.status === "Received" ||
    r.status === "Received (Discrepancy)"
  );

  const rows = (isCentral || isAdmin) ? centralItems : siteItems;

  const awaitingDispatch = materialRequests.filter(r => r.status === "Approved").length;
  const awaitingCentralApproval = materialRequests.filter(r => r.status === "Pending Central Approval").length;

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
              ? (awaitingCentralApproval > 0
                  ? `${awaitingCentralApproval} site-to-site transfer${awaitingCentralApproval === 1 ? "" : "s"} need your approval`
                  : `${awaitingDispatch} awaiting dispatch`)
              : `${siteItems.filter(r => r.status === "In Transit").length} in transit`,
            variant: awaitingCentralApproval > 0 || awaitingDispatch > 0 || siteItems.filter(r => r.status === "In Transit").length > 0 ? "warning" : "success",
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
              <Fragment key={r.id}>
              <tr>
                <td style={{ fontWeight: 600 }}>{r.requestNo}</td>
                <td>{r.project}</td>
                <td style={{ fontSize: 13, color: "#64748b" }}>{linesSummary(r)}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                  {r.status === "Pending Central Approval" && (
                    <div style={{ fontSize: 12, color: "#64748b", marginTop: 4, display: "flex", alignItems: "center", gap: 4 }}>
                      <ArrowLeftRight size={12} /> {r.sourceStore} → {r.requestingStore}
                    </div>
                  )}
                </td>
                <td>
                  {/* Site-to-site transfer awaiting Central's own sign-off — dispatch is blocked */}
                  {r.status === "Pending Central Approval" && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <button
                        className="ch-btn ch-btn--outline"
                        style={{ padding: "4px 10px", fontSize: 13 }}
                        onClick={() => setExpandedId(expandedId === r.id ? null : r.id)}
                      >
                        {expandedId === r.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                        {" "}Details
                      </button>
                      {(isCentral || isAdmin) ? (
                        <>
                          <button
                            className="ch-btn ch-btn--primary"
                            disabled={busyId === r.id}
                            onClick={() => onCentralApprove(r)}
                          >
                            {busyId === r.id ? "Approving…" : "Approve"}
                          </button>
                          <button
                            className="ch-btn ch-btn--danger"
                            disabled={busyId === r.id}
                            onClick={() => onCentralReject(r)}
                          >
                            Reject
                          </button>
                        </>
                      ) : (
                        <span style={{ color: "#64748b", fontSize: 13 }}>Waiting on Central Store's approval</span>
                      )}
                    </div>
                  )}

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
              {expandedId === r.id && r.status === "Pending Central Approval" && (
                <tr>
                  <td colSpan={5} style={{ background: "#f8fafc", padding: "14px 20px" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12, fontSize: 13 }}>
                      <div>
                        <div style={{ color: "#94a3b8", marginBottom: 2 }}>Requesting store</div>
                        <div style={{ fontWeight: 600 }}>{r.requestingStore}</div>
                      </div>
                      <div>
                        <div style={{ color: "#94a3b8", marginBottom: 2 }}>Requested by</div>
                        <div style={{ fontWeight: 600 }}>{r.requestedBy}</div>
                      </div>
                      <div>
                        <div style={{ color: "#94a3b8", marginBottom: 2 }}>Source store</div>
                        <div style={{ fontWeight: 600 }}>{r.sourceStore}</div>
                      </div>
                      <div>
                        <div style={{ color: "#94a3b8", marginBottom: 2 }}>Source store manager (already approved)</div>
                        <div style={{ fontWeight: 600 }}>{r.sourceStoreManager}</div>
                      </div>
                    </div>
                    <div style={{ marginTop: 12 }}>
                      <div style={{ color: "#94a3b8", marginBottom: 4, fontSize: 13 }}>Items requested</div>
                      <table className="table" style={{ margin: 0 }}>
                        <thead>
                          <tr><th>Item</th><th>Requested</th><th>Approved (by {r.sourceStore})</th></tr>
                        </thead>
                        <tbody>
                          {r.lines.map((l, i) => (
                            <tr key={i}>
                              <td>{l.item}{l.uom ? ` (${l.uom})` : ""}</td>
                              <td>{l.requested}</td>
                              <td>{l.approved ?? "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </td>
                </tr>
              )}
              </Fragment>
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
