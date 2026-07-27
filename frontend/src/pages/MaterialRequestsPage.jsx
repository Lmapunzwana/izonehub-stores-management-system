import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { Plus, Check, X, ChevronDown, Package, CheckCircle2, AlertTriangle } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

const STATUS_TYPE = {
  Draft: "default",
  "Pending Approval": "warning",
  Approved: "info",
  Rejected: "danger",
  "In Transit": "info",
  Received: "success",
  "Received (Discrepancy)": "danger",
};

export default function MaterialRequestsPage() {
  const navigate = useNavigate();
  const { materialRequests, approveMaterialRequest, rejectMaterialRequest, markRequestReceived, user } = useAppData();
  const { showAlert } = useAppModal();
  const [filter, setFilter] = useState("ALL");
  const [busyId, setBusyId] = useState(null);

  const [approveModal, setApproveModal] = useState(null); // { request, quantities }
  const [rejectModalId, setRejectModalId] = useState(null);
  const [rejectReason, setRejectReason] = useState("");
  const [viewItemsModal, setViewItemsModal] = useState(null);

  // Per-line Receive Modal
  const [receiveModal, setReceiveModal] = useState(null); // { request, quantities }

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite = user?.roles?.includes("SITE_STORE_MANAGER");

  const pendingCount = materialRequests.filter((r) => r.status === "Pending Approval").length;

  const visibleRequests = useMemo(() => {
    let list = materialRequests;

    if (isSite && !isAdmin && !isCentral) {
      list = list.filter(r => {
        const isRequester = r.original?.raisedBy?.id === user?.id;
        const isAskingStore = r.original?.requestingStore?.manager?.id === user?.id || user?.assignedStoreId === r.original?.requestingStore?.id;
        return isRequester || isAskingStore;
      });
    }

    if (filter === "PENDING") list = list.filter((r) => r.status === "Pending Approval");
    else if (filter === "APPROVED") list = list.filter((r) => r.status === "Approved" || r.status === "In Transit");
    else if (filter === "COMPLETED") list = list.filter((r) => r.status === "Received" || r.status === "Received (Discrepancy)");
    else if (filter === "REJECTED") list = list.filter((r) => r.status === "Rejected");

    return list;
  }, [materialRequests, filter, isSite, isAdmin, isCentral, user]);

  function openApproveModal(r) {
    const originalLines = r.original?.lines || [];
    setApproveModal({
      request: r,
      quantities: (r.lines || []).map((l, idx) => {
        const orig = originalLines[idx] || {};
        const requested = Number(orig.requestedQuantity || l.quantity || 0);
        return {
          lineId: orig.id,
          item: l.item,
          uom: l.uom || orig.item?.unitOfMeasure || "",
          requested,
          approvedQty: requested,
        };
      }),
    });
  }

  function openReceiveModal(r) {
    const originalLines = r.original?.lines || [];
    setReceiveModal({
      request: r,
      quantities: (r.lines || []).map((l, idx) => {
        const orig = originalLines[idx] || {};
        const dispatched = Number(l.dispatched || orig.dispatchedQuantity || orig.approvedQuantity || orig.requestedQuantity || 0);
        return {
          lineId: orig.id,
          item: l.item,
          uom: l.uom || orig.item?.unitOfMeasure || "",
          dispatched,
          receivedQty: dispatched,
        };
      }),
    });
  }

  async function onConfirmApprove() {
    if (!approveModal) return;
    setBusyId(approveModal.request.id);
    try {
      const qList = approveModal.quantities.map(q => Number(q.approvedQty) || 0);
      await approveMaterialRequest(approveModal.request.id, qList);
      setApproveModal(null);
      showAlert({ title: "Approved", message: `Request #${approveModal.request.requestNo} has been approved.`, type: "success" });
    } catch (e) {
      showAlert({ title: "Error", message: "Failed to approve request. " + (e?.message || ""), type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  async function onConfirmReceive() {
    if (!receiveModal) return;
    setBusyId(receiveModal.request.id);
    try {
      const qList = receiveModal.quantities.map(q => Number(q.receivedQty) || 0);
      await markRequestReceived(receiveModal.request.id, qList);
      setReceiveModal(null);
      showAlert({ title: "Receipt Confirmed", message: `Request #${receiveModal.request.requestNo} marked as received into site store.`, type: "success" });
    } catch (e) {
      showAlert({ title: "Error", message: "Failed to process receipt. " + (e?.message || ""), type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  async function onReject() {
    if (!rejectModalId || !rejectReason.trim()) return;
    setBusyId(rejectModalId);
    try {
      await rejectMaterialRequest(rejectModalId, rejectReason.trim());
      setRejectModalId(null);
      setRejectReason("");
    } catch (e) {
      showAlert({ title: "Error", message: "Failed to reject request.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Material Requests"
          badge={`${visibleRequests.length} requests`}
          actions={[
            {
              label: "New Request",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => navigate("/material-requests/new"),
            },
          ]}
          status={{
            label: `${pendingCount} pending approval`,
            variant: pendingCount > 0 ? "warning" : "success",
          }}
        />

        <div className="filters">
          <div className="button-group">
            {["ALL", "PENDING", "APPROVED", "COMPLETED", "REJECTED"].map((f) => (
              <button
                key={f}
                type="button"
                className={`btn btn-sm ${filter === f ? "btn-primary" : "btn-outline"}`}
                onClick={() => setFilter(f)}
              >
                {f.charAt(0) + f.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>

        <table className="table">
          <thead>
            <tr>
              <th>Req No</th>
              <th>Project Title & Code</th>
              <th>Requesting Site Store</th>
              <th>Items Requested</th>
              <th>Asking Manager</th>
              <th>Status</th>
              <th>Action</th>
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
                  ) : r.status === "In Transit" ? (
                    (isSite || isAdmin) ? (
                      <button
                        type="button"
                        className="ch-btn ch-btn--success"
                        disabled={busyId === r.id}
                        onClick={() => openReceiveModal(r)}
                      >
                        <CheckCircle2 size={16} />
                        Receive Items
                      </button>
                    ) : (
                      <button className="ch-btn ch-btn--outline" onClick={() => navigate("/dispatch")}>
                        View Dispatch
                      </button>
                    )
                  ) : r.status === "Approved" ? (
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

      {/* Interactive Per-Line Receive Modal */}
      {receiveModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 640, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Receive Items for Request #{receiveModal.request.requestNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Inspect and enter the actual physical quantities received at your site store. Any missing or extra quantity will automatically generate a discrepancy log.
            </p>
            <div style={{ maxHeight: 340, overflowY: "auto", marginBottom: 20 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>UOM</th>
                    <th style={{ textAlign: "right" }}>Dispatched</th>
                    <th style={{ textAlign: "right" }}>Actual Received Qty</th>
                  </tr>
                </thead>
                <tbody>
                  {receiveModal.quantities.map((l, idx) => {
                    const diff = Number(l.receivedQty) - Number(l.dispatched);
                    return (
                      <tr key={idx}>
                        <td style={{ fontWeight: 500 }}>{l.item}</td>
                        <td style={{ color: "#64748b", fontSize: 13 }}>{l.uom || "—"}</td>
                        <td style={{ textAlign: "right", color: "#475569" }}>{l.dispatched}</td>
                        <td style={{ textAlign: "right" }}>
                          <input
                            type="number"
                            className="input"
                            min="0"
                            step="any"
                            style={{ width: 90, textAlign: "right", padding: "4px 8px", fontSize: 13 }}
                            value={l.receivedQty}
                            onChange={e => {
                              const updated = [...receiveModal.quantities];
                              updated[idx] = { ...updated[idx], receivedQty: e.target.value };
                              setReceiveModal(a => ({ ...a, quantities: updated }));
                            }}
                          />
                          {diff !== 0 && (
                            <div style={{ fontSize: 11, color: diff < 0 ? "#dc2626" : "#f59e0b", marginTop: 2 }}>
                              {diff < 0 ? `Missing ${Math.abs(diff)}` : `Over +${diff}`} (Discrepancy)
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="modal-actions" style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button className="btn btn-outline" onClick={() => setReceiveModal(null)}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                disabled={busyId === receiveModal.request.id}
                onClick={onConfirmReceive}
              >
                {busyId === receiveModal.request.id ? "Processing Receipt…" : "Confirm & Deposit to Inventory"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Per-item Approval Modal */}
      {approveModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 620, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Approve Request #{approveModal.request.requestNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Review and adjust quantities per item. Set to <strong>0</strong> to exclude an item.
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
              Provide a reason for rejection. This will be recorded in the audit log.
            </p>
            <textarea
              className="input"
              rows={3}
              placeholder="e.g. Insufficient budget allocation…"
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
                {busyId === rejectModalId ? "Rejecting…" : "Reject Request"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* View Items Detail Modal */}
      {viewItemsModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 540, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Items in Request #{viewItemsModal.requestNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              {viewItemsModal.project} — Requesting Store: {viewItemsModal.requestingStore}
            </p>
            <table className="table" style={{ marginBottom: 20 }}>
              <thead>
                <tr>
                  <th>Item</th>
                  <th>UOM</th>
                  <th style={{ textAlign: "right" }}>Requested</th>
                  <th style={{ textAlign: "right" }}>Approved</th>
                  <th style={{ textAlign: "right" }}>Dispatched</th>
                  <th style={{ textAlign: "right" }}>Received</th>
                </tr>
              </thead>
              <tbody>
                {viewItemsModal.lines?.map((l, i) => (
                  <tr key={i}>
                    <td style={{ fontWeight: 500 }}>{l.item}</td>
                    <td style={{ color: "#64748b", fontSize: 13 }}>{l.uom || "—"}</td>
                    <td style={{ textAlign: "right" }}>{l.quantity}</td>
                    <td style={{ textAlign: "right" }}>{l.approved ?? l.quantity}</td>
                    <td style={{ textAlign: "right", color: "#2563eb" }}>{l.dispatched ?? "—"}</td>
                    <td style={{ textAlign: "right", color: "#16a34a" }}>{l.received ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <button className="btn btn-outline" onClick={() => setViewItemsModal(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
