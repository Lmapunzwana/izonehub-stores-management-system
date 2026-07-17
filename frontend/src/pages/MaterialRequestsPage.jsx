import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Check, X } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

const STATUS_TYPE = {
  "Pending Approval": "warning",
  Approved:  "info",
  "In Transit": "warning",
  Received:  "success",
  "Received (Discrepancy)": "danger",
  Rejected:  "danger",
  Draft:     "default",
};

export default function MaterialRequestsPage() {
  const navigate = useNavigate();
  const { materialRequests, approveRequest, rejectRequest, user } = useAppData();
  const { showAlert } = useAppModal();

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");

  const [rejectModalId, setRejectModalId] = useState(null);
  const [rejectReason, setRejectReason] = useState("");
  const [busyId, setBusyId] = useState(null);
  const [viewItemsModal, setViewItemsModal] = useState(null);

  // Site manager only sees requests from their projects
  const visibleRequests = (isCentral || isAdmin)
    ? materialRequests
    : materialRequests.filter(r =>
        r.original?.raisedBy?.id === user?.id ||
        r.original?.requestingStore?.id === user?.assignedStoreId
      );

  async function onApprove(r) {
    setBusyId(r.id);
    try {
      await approveRequest(r.id);
    } catch (e) {
      showAlert({ title: "Error", message: e?.message || "Failed to approve request.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  async function onReject() {
    if (!rejectReason.trim()) {
      showAlert({ title: "Reason Required", message: "Please enter a reason for rejection.", type: "warning" });
      return;
    }
    setBusyId(rejectModalId);
    try {
      await rejectRequest(rejectModalId, rejectReason);
      setRejectModalId(null);
      setRejectReason("");
    } catch (e) {
      showAlert({ title: "Error", message: e?.message || "Failed to reject request.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  function linesSummary(r) {
    if (!r.lines || r.lines.length === 0) return "—";
    return r.lines.map(l => `${l.item} × ${l.requested}`).join(", ");
  }

  const actions = [];
  if (isSite) {
    actions.push({
      label: "New Request",
      icon: <Plus size={16} />,
      variant: "primary",
      onClick: () => navigate("/material-requests/add-item"),
    });
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Material Requests"
          badge={isCentral || isAdmin ? `${materialRequests.filter(r => r.status === "Pending Approval").length} pending` : undefined}
          actions={actions}
        />

        <table className="table">
          <thead>
            <tr>
              <th>Request No</th>
              <th>Project</th>
              <th>Items</th>
              <th>Requested By</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {visibleRequests.map((r) => (
              <tr key={r.requestNo}>
                <td style={{ fontWeight: 600 }}>{r.requestNo}</td>
                <td>{r.project}</td>
                <td>
                  <button className="ch-btn ch-btn--outline" onClick={() => setViewItemsModal(r)} style={{ padding: "4px 8px", fontSize: 12 }}>
                    View Items ({r.lines?.length || 0})
                  </button>
                </td>
                <td style={{ fontSize: 13 }}>{r.requestedBy}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Pending Approval" && (isCentral || isAdmin) ? (
                    <div className="action-buttons">
                      <button
                        type="button"
                        className="ch-btn ch-btn--success"
                        disabled={busyId === r.id}
                        onClick={() => onApprove(r)}
                      >
                        <Check size={16} />
                        {busyId === r.id ? "Approving…" : "Approve"}
                      </button>
                      <button
                        type="button"
                        className="ch-btn ch-btn--danger"
                        disabled={busyId === r.id}
                        onClick={() => { setRejectModalId(r.id); setRejectReason(""); }}
                      >
                        <X size={16} />
                        Reject
                      </button>
                    </div>
                  ) : r.status === "Approved" || r.status === "In Transit" ? (
                    (isCentral || isAdmin) ? (
                      <button className="ch-btn ch-btn--outline" onClick={() => navigate("/dispatch")}>
                        Go to Dispatch
                      </button>
                    ) : (
                      <span style={{ color: "#64748b", fontSize: 13 }}>Awaiting dispatch</span>
                    )
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>—</span>
                  )}
                </td>
              </tr>
            ))}
            {visibleRequests.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b", padding: "24px 0" }}>
                  No material requests found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Reject reason modal */}
      {rejectModalId && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 440, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 8 }}>Reject Request</h3>
            <p style={{ color: "#64748b", fontSize: 14, marginBottom: 16 }}>
              Provide a reason for rejection. This will be recorded in the audit log and visible to the requester.
            </p>
            <textarea
              className="input"
              rows={3}
              placeholder="e.g. Insufficient budget allocation for this period…"
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              style={{ width: "100%", resize: "vertical" }}
              autoFocus
            />
            <div className="modal-actions" style={{ marginTop: 20, display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button className="btn btn-outline" onClick={() => setRejectModalId(null)}>
                Cancel
              </button>
              <button
                className="btn btn-danger"
                disabled={!rejectReason.trim() || busyId === rejectModalId}
                onClick={onReject}
              >
                {busyId === rejectModalId ? "Rejecting…" : "Confirm Rejection"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* View Items modal */}
      {viewItemsModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 600, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 8 }}>Items for Request #{viewItemsModal.requestNo}</h3>
            <div style={{ maxHeight: '400px', overflowY: 'auto', marginBottom: '20px' }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Requested</th>
                    <th>Approved</th>
                    <th>Dispatched</th>
                    <th>Received</th>
                  </tr>
                </thead>
                <tbody>
                  {(viewItemsModal.lines || []).map((l, idx) => (
                    <tr key={idx}>
                      <td>{l.item}</td>
                      <td>{l.requested}</td>
                      <td>{l.approved}</td>
                      <td>{l.dispatched}</td>
                      <td>{l.received}</td>
                    </tr>
                  ))}
                  {(!viewItemsModal.lines || viewItemsModal.lines.length === 0) && (
                    <tr><td colSpan={5} style={{ textAlign: "center" }}>No items found</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="modal-actions" style={{ display: "flex", justifyContent: "flex-end" }}>
              <button className="ch-btn ch-btn--outline" onClick={() => setViewItemsModal(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
