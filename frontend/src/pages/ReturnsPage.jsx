import { useState } from "react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { CheckCircle2, RotateCcw, AlertTriangle } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function ReturnsPage() {
  const { returnsList, materialRequests, confirmReturn, initiateReturn, refreshItems, user } = useAppData();
  const { showAlert } = useAppModal();

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");

  const awaitingCount = returnsList.filter(r => r.status === "Awaiting Confirmation").length;

  // --- Site Manager: Initiate Return ---
  // Only completed requests that have received quantities
  const completedRequests = materialRequests.filter(
    r => r.status === "Received" || r.status === "Received (Discrepancy)"
  );

  const [initiateModal, setInitiateModal] = useState(false);
  const [selectedRequestId, setSelectedRequestId] = useState(completedRequests[0]?.id || "");
  const [returnLines, setReturnLines] = useState([]);

  function openInitiateModal() {
    const req = completedRequests.find(r => r.id === selectedRequestId) || completedRequests[0];
    if (!req) return;
    setSelectedRequestId(req.id);
    buildLines(req);
    setInitiateModal(true);
  }

  function buildLines(req) {
    setReturnLines(
      (req.lines || []).map(l => {
        const received   = Number(l.received) || Number(l.dispatched) || 0;
        const consumed   = Number(l.consumed) || 0;
        const maxReturn  = Math.max(0, received - consumed);
        return {
          itemId:      req.original?.lines?.find(ol => ol.item?.name === l.item)?.item?.id,
          itemName:    l.item,
          received,
          consumed,
          maxReturn,
          quantity:    "",
          condition:   "SERVICEABLE",
        };
      })
    );
  }

  function onSelectRequest(reqId) {
    setSelectedRequestId(reqId);
    const req = completedRequests.find(r => r.id === reqId);
    if (req) buildLines(req);
  }

  async function handleInitiateReturn(e) {
    e.preventDefault();
    const payloadLines = returnLines
      .filter(l => l.quantity > 0 && l.itemId)
      .map(({ itemId, quantity, condition }) => ({ itemId, quantity: Number(quantity), condition }));

    if (payloadLines.length === 0) {
      showAlert({ title: "Validation Error", message: "Enter a return quantity for at least one item.", type: "warning" });
      return;
    }

    // Validate no line exceeds max returnable
    for (const l of returnLines) {
      if (l.quantity && Number(l.quantity) > l.maxReturn) {
        showAlert({
          title: "Quantity Exceeds Limit",
          message: `Cannot return more than ${l.maxReturn} units of ${l.itemName} (received ${l.received}, consumed ${l.consumed}).`,
          type: "danger"
        });
        return;
      }
    }

    try {
      await initiateReturn(selectedRequestId, payloadLines);
      setInitiateModal(false);
      await refreshItems();
      showAlert({ title: "Return Initiated", message: "Return has been submitted and is awaiting central store confirmation.", type: "success" });
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to initiate return. " + (err?.message || ""), type: "danger" });
    }
  }

  // --- Central Manager: Confirm Return ---
  const [confirmModal, setConfirmModal] = useState(null);
  const [collectorName, setCollectorName] = useState("");
  const [actualQty, setActualQty] = useState({});
  const [confirming, setConfirming] = useState(false);

  function openConfirmModal(ret) {
    setConfirmModal(ret);
    setCollectorName("");
    setActualQty({});
  }

  async function handleConfirmReturn(e) {
    e.preventDefault();
    if (!collectorName.trim()) {
      showAlert({ title: "Collector Required", message: "Please enter the collector's name.", type: "warning" });
      return;
    }
    setConfirming(true);
    try {
      await confirmReturn(confirmModal.id);
      setConfirmModal(null);
      await refreshItems();
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to confirm return. " + (err?.message || ""), type: "danger" });
    } finally {
      setConfirming(false);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Project Returns"
          actions={isSite ? [
            {
              label: "Initiate Return",
              icon: <RotateCcw size={16} />,
              variant: "primary",
              onClick: openInitiateModal,
              disabled: completedRequests.length === 0,
            }
          ] : []}
          status={{
            label: `${awaitingCount} awaiting confirmation`,
            variant: awaitingCount > 0 ? "warning" : "success",
          }}
        />

        {isSite && completedRequests.length === 0 && (
          <div style={{ padding: "16px", color: "#64748b", fontSize: 14 }}>
            No completed material requests found. You can only return items from received requests.
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Return No</th>
              <th>Project</th>
              <th>Items</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {returnsList.map(r => (
              <tr key={r.returnNo}>
                <td style={{ fontWeight: 600 }}>{r.returnNo}</td>
                <td>{r.project}</td>
                <td style={{ fontSize: 13, color: "#64748b" }}>
                  {r.original.lines?.map(l => `${l.item?.name} × ${l.quantity}`).join(", ") || "—"}
                </td>
                <td>
                  <Badge type={r.status === "Confirmed" ? "success" : "warning"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Awaiting Confirmation" && (isCentral || isAdmin) ? (
                    <button className="ch-btn ch-btn--success" onClick={() => openConfirmModal(r)}>
                      <CheckCircle2 size={16} />
                      Confirm Return
                    </button>
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>—</span>
                  )}
                </td>
              </tr>
            ))}
            {returnsList.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", color: "#64748b", padding: "24px 0" }}>
                  No returns on record.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Site Manager: Initiate Return Modal */}
      {initiateModal && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh", overflowY: "auto" }}>
          <div className="app-modal" style={{ maxWidth: 640, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Initiate Return to Central Store</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 20 }}>
              You can only return unused inventory. Consumed quantities cannot be returned.
            </p>

            <div style={{ marginBottom: 16 }}>
              <label style={{ display: "block", fontWeight: 500, marginBottom: 6 }}>Select Request</label>
              <select
                className="input"
                value={selectedRequestId}
                onChange={e => onSelectRequest(e.target.value)}
              >
                {completedRequests.map(r => (
                  <option key={r.id} value={r.id}>{r.requestNo} — {r.project}</option>
                ))}
              </select>
            </div>

            <form onSubmit={handleInitiateReturn}>
              <table className="table" style={{ marginBottom: 16 }}>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Received</th>
                    <th>Consumed</th>
                    <th>Max Returnable</th>
                    <th>Return Qty</th>
                    <th>Condition</th>
                  </tr>
                </thead>
                <tbody>
                  {returnLines.map((line, idx) => (
                    <tr key={idx}>
                      <td>{line.itemName}</td>
                      <td>{line.received}</td>
                      <td>{line.consumed}</td>
                      <td style={{ fontWeight: 600, color: line.maxReturn === 0 ? "#dc2626" : "#10b981" }}>
                        {line.maxReturn}
                      </td>
                      <td>
                        <input
                          type="number"
                          className="input"
                          min="0"
                          max={line.maxReturn}
                          step="any"
                          disabled={line.maxReturn === 0}
                          value={line.quantity || ""}
                          onChange={e => {
                            const nl = [...returnLines];
                            nl[idx].quantity = e.target.value;
                            setReturnLines(nl);
                          }}
                          style={{ width: 80 }}
                        />
                      </td>
                      <td>
                        <select
                          className="input"
                          value={line.condition}
                          disabled={line.maxReturn === 0}
                          onChange={e => {
                            const nl = [...returnLines];
                            nl[idx].condition = e.target.value;
                            setReturnLines(nl);
                          }}
                        >
                          <option value="SERVICEABLE">Serviceable</option>
                          <option value="DAMAGED">Damaged</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div style={{ background: "#fef3c7", border: "1px solid #fde68a", borderRadius: 6, padding: "10px 14px", fontSize: 13, color: "#92400e", marginBottom: 20, display: "flex", gap: 8, alignItems: "flex-start" }}>
                <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: 1 }} />
                Any variance between the returned quantity and the amount confirmed by the central store will automatically create a discrepancy.
              </div>

              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setInitiateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Submit Return
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Central Manager: Confirm Return Modal */}
      {confirmModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 440, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 8 }}>Confirm Return — {confirmModal.returnNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Physically receive the items back at the central store. Any quantity difference from the expected return will create a discrepancy.
            </p>
            <form onSubmit={handleConfirmReturn}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 6 }}>Collector / Handler Name</label>
                <input
                  className="input"
                  placeholder="Name of person collecting the return"
                  value={collectorName}
                  onChange={e => setCollectorName(e.target.value)}
                  required
                  autoFocus
                />
              </div>

              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setConfirmModal(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-success" disabled={!collectorName.trim() || confirming}>
                  <CheckCircle2 size={16} />
                  {confirming ? "Confirming…" : "Confirm Received"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
