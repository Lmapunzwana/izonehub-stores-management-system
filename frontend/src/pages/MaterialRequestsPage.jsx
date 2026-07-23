import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Check, X, ChevronDown } from "lucide-react";
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

  // Per-item approval modal state
  const [approveModal, setApproveModal] = useState(null); // { request, quantities: [{...line, approvedQty}] }

  // Site manager sees requests raised by them or linked to their assigned/managed store
  const visibleRequests = (isCentral || isAdmin)
    ? materialRequests
    : materialRequests.filter(r =>
        r.original?.raisedBy?.id === user?.id ||
        r.original?.raisedBy?.email === user?.email ||
        r.original?.requestingStore?.id === user?.assignedStoreId ||
        r.original?.requestingStore?.manager?.id === user?.id ||
        r.original?.sourceStore?.id === user?.assignedStoreId ||
        r.original?.sourceStore?.manager?.id === user?.id
      );

  // Open per-item approval modal
  function openApproveModal(r) {
    const quantities = (r.lines || []).map(l => ({
      ...l,
      approvedQty: String(l.requested ?? ""),
    }));
    setApproveModal({ request: r, quantities });
  }

  async function onConfirmApprove() {
    if (!approveModal) return;
    const quantities = approveModal.quantities.map(l => {
      const v = Number(l.approvedQty);
      return isNaN(v) ? 0 : Math.max(0, v);
    });
    setBusyId(approveModal.request.id);
    try {
      await approveRequest(approveModal.request.id, quantities);
      setApproveModal(null);
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
              <th>Requesting Store</th>
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
                <td>
                  <div style={{ fontWeight: 500 }}>{r.project}</div>
                  {r.projectCode && (
                    <div style={{ fontSize: 11, color: "#94a3b8", fontFamily: "monospace" }}>{r.projectCode}</div>
                  )}
                </td>
                <td style={{ fontSize: 13, color: "#475569" }}>{r.requestingStore}</td>
                <td>
                  <button className="ch-btn ch-btn--outline" onClick={() => setViewItemsModal(r)} style={{ padding: "4px 8px", fontSize: 12 }}>
                    View Items ({r.lines?.length || 0})
                  </button>
                </td>
                <td style={{ fontSize: 13 }}>{r.requestedBy}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                  {r.original?.rejectionReason && (
                    <div style={{ fontSize: 11, color: "#dc2626", marginTop: 2 }}>{r.original.rejectionReason}</div>
                  )}
                </td>
                <td>
                  {r.status === "Pending Approval" && (isCentral || isAdmin) ? (
                    <div className="action-buttons">
                      <button
                        type="button"
                        className="ch-btn ch-btn--success"
                        disabled={busyId === r.id}
                        onClick={() => openApproveModal(r)}
                      >
                        <Check size={16} />
                        Approve
                        <ChevronDown size={13} style={{ marginLeft: 2 }} />
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
                <td colSpan={7} style={{ textAlign: "center", color: "#64748b", padding: "24px 0" }}>
                  No material requests found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Per-item Approval Modal */}
      {approveModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 620, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Approve Request #{approveModal.request.requestNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Review and adjust quantities per item. Set to <strong>0</strong> to exclude an item. Other lines will still be approved.
            </p>
            <div style={{ maxHeight: 340, overflowY: "auto", marginBottom: 20 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>UOM</th>
                    <th style={{ textAlign: "right" }}>Requested</th>
                    <th style={{ textAlign: "right" }}>Approve Qty</th>
                  </tr>
                </thead>
                <tbody>
                  {approveModal.quantities.map((l, idx) => (
                    <tr key={idx}>
                      <td style={{ fontWeight: 500 }}>{l.item}</td>
                      <td style={{ color: "#64748b", fontSize: 13 }}>{l.uom || "—"}</td>
                      <td style={{ textAlign: "right", color: "#475569" }}>{l.requested}</td>
                      <td style={{ textAlign: "right" }}>
                        <input
                          type="number"
                          className="input"
                          min="0"
                          max={l.requested}
                          style={{ width: 90, textAlign: "right", padding: "4px 8px", fontSize: 13 }}
                          value={l.approvedQty}
                          onChange={e => {
                            const updated = [...approveModal.quantities];
                            updated[idx] = { ...updated[idx], approvedQty: e.target.value };
                            setApproveModal(a => ({ ...a, quantities: updated }));
                          }}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ fontSize: 12, color: "#94a3b8", marginBottom: 16 }}>
              Items set to 0 will not be issued. The remainder of the request will proceed as approved.
            </div>
            <div className="modal-actions" style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button className="btn btn-outline" onClick={() => setApproveModal(null)}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                disabled={busyId === approveModal.request.id}
                onClick={onConfirmApprove}
              >
                {busyId === approveModal.request.id ? "Approving…" : "Confirm Approval"}
              </button>
            </div>
          </div>
        </div>
      )}

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
          <div className="app-modal" style={{ maxWidth: 640, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Items for Request #{viewItemsModal.requestNo}</h3>
            <div style={{ fontSize: 13, color: "#64748b", marginBottom: 12 }}>
              Project: <strong>{viewItemsModal.project}</strong> &nbsp;|&nbsp;
              Store: <strong>{viewItemsModal.requestingStore}</strong> &nbsp;|&nbsp;
              Requested By: <strong>{viewItemsModal.requestedBy}</strong>
            </div>
            <div style={{ maxHeight: "400px", overflowY: "auto", marginBottom: "20px" }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>UOM</th>
                    <th>Requested</th>
                    <th>Approved</th>
                    <th>Dispatched</th>
                    <th>Received</th>
                  </tr>
                </thead>
                <tbody>
                  {(viewItemsModal.lines || []).map((l, idx) => (
                    <tr key={idx}>
                      <td style={{ fontWeight: 500 }}>{l.item}</td>
                      <td style={{ color: "#64748b", fontSize: 13 }}>{l.uom || "—"}</td>
                      <td style={{ fontWeight: 600 }}>{l.requested ?? "—"}</td>
                      <td style={{ color: l.approved != null && l.approved > 0 ? "#16a34a" : l.approved === 0 ? "#dc2626" : "#64748b" }}>
                        {l.approved != null ? l.approved : "—"}
                      </td>
                      <td>{l.dispatched ?? "—"}</td>
                      <td style={{ color: l.received > 0 ? "#2563eb" : undefined }}>{l.received ?? "—"}</td>
                    </tr>
                  ))}
                  {(!viewItemsModal.lines || viewItemsModal.lines.length === 0) && (
                    <tr><td colSpan={6} style={{ textAlign: "center" }}>No items found</td></tr>
                  )}
                  {viewItemsModal.lines && viewItemsModal.lines.length > 0 && (() => {
                    const uomTotals = viewItemsModal.lines.reduce((acc, l) => {
                      const uom = l.uom || "units";
                      acc[uom] = (acc[uom] || 0) + Number(l.requested || 0);
                      return acc;
                    }, {});
                    return (
                      <tr style={{ background: "#f8fafc", fontWeight: 600, borderTop: "2px solid #e2e8f0" }}>
                        <td style={{ color: "#475569" }}>Total ({viewItemsModal.lines.length} line{viewItemsModal.lines.length !== 1 ? "s" : ""})</td>
                        <td></td>
                        <td colSpan={4} style={{ color: "#1e293b" }}>
                          {Object.entries(uomTotals).map(([uom, qty]) => `${qty} ${uom}`).join(" + ")}
                        </td>
                      </tr>
                    );
                  })()}
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
