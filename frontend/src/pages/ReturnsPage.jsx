import { useState, useEffect } from "react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { CheckCircle2, RotateCcw, AlertTriangle, Package } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function ReturnsPage() {
  const { returnsList, materialRequests, confirmReturn, initiateReturn, refreshItems, user, stores } = useAppData();
  const { showAlert } = useAppModal();

  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");

  const awaitingCount = returnsList.filter(r => r.status === "Awaiting Confirmation").length;

  // Completed requests reference
  const completedRequests = materialRequests.filter(
    r => r.status === "Received" || r.status === "Received (Discrepancy)"
  );

  const [initiateModal, setInitiateModal] = useState(false);
  const [returnSource, setReturnSource] = useState("inventory"); // "inventory" or "request"
  const [selectedRequestId, setSelectedRequestId] = useState(completedRequests[0]?.id || "");
  const [siteInventory, setSiteInventory] = useState([]);
  const [returnLines, setReturnLines] = useState([]);
  const [initiating, setInitiating] = useState(false);
  const [loadingInv, setLoadingInv] = useState(false);

  // Available site stores
  const activeStores = stores.filter(s => s.active && !s.closing);
  const siteStore = activeStores.find(s => s.type === "SITE") || activeStores[0];

  function openInitiateModal() {
    setInitiateModal(true);
    if (returnSource === "inventory") {
      loadSiteInventoryLines();
    } else {
      const req = completedRequests.find(r => r.id === selectedRequestId) || completedRequests[0];
      if (req) buildLinesFromRequest(req);
    }
  }

  function loadSiteInventoryLines() {
    setLoadingInv(true);
    const query = siteStore ? `?storeId=${siteStore.id}` : "";
    apiFetch(`/api/inventory/site-inventory${query}`)
      .then((res) => {
        const list = Array.isArray(res) ? res : [];
        setSiteInventory(list);
        setReturnLines(
          list.map(inv => ({
            itemId: inv.itemId,
            itemName: inv.itemName,
            itemCode: inv.itemCode,
            onHand: Number(inv.onHand || 0),
            maxReturn: Math.max(0, Number(inv.available || 0)),
            quantity: "",
            condition: "SERVICEABLE",
          }))
        );
      })
      .catch((err) => console.error("Failed to load site inventory for returns", err))
      .finally(() => setLoadingInv(false));
  }

  function buildLinesFromRequest(req) {
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

  useEffect(() => {
    if (initiateModal && returnSource === "inventory") {
      loadSiteInventoryLines();
    } else if (initiateModal && returnSource === "request") {
      const req = completedRequests.find(r => r.id === selectedRequestId) || completedRequests[0];
      if (req) buildLinesFromRequest(req);
    }
  }, [returnSource, selectedRequestId, initiateModal]);

  async function handleInitiateReturn(e) {
    e.preventDefault();
    const payloadLines = returnLines
      .filter(l => Number(l.quantity) > 0 && l.itemId)
      .map(({ itemId, quantity, condition }) => ({ itemId, quantity: Number(quantity), condition }));

    if (payloadLines.length === 0) {
      showAlert({ title: "Validation Error", message: "Enter a return quantity greater than 0 for at least one item.", type: "warning" });
      return;
    }

    for (const l of returnLines) {
      if (l.quantity && Number(l.quantity) > l.maxReturn) {
        showAlert({
          title: "Quantity Exceeds Limit",
          message: `Cannot return more than ${l.maxReturn} available units of ${l.itemName}.`,
          type: "danger"
        });
        return;
      }
    }

    setInitiating(true);
    try {
      if (returnSource === "request" && selectedRequestId) {
        await initiateReturn(selectedRequestId, payloadLines);
      } else {
        await apiFetch("/api/returns", {
          method: "POST",
          body: {
            storeId: siteStore?.id,
            lines: payloadLines,
          }
        });
      }
      setInitiateModal(false);
      await refreshItems();
      showAlert({ title: "Return Initiated", message: "Return submitted successfully and awaiting Central Store confirmation.", type: "success" });
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to initiate return. " + (err?.message || ""), type: "danger" });
    } finally {
      setInitiating(false);
    }
  }

  // --- Central Manager: Confirm Return Modal ---
  const [confirmModal, setConfirmModal] = useState(null); // { return, lines }
  const [collectorName, setCollectorName] = useState("");
  const [confirming, setConfirming] = useState(false);

  function openConfirmModal(ret) {
    const lines = ret.original?.lines || [];
    setConfirmModal({
      returnObj: ret,
      lines: lines.map(l => ({
        itemId: l.item?.id,
        itemName: l.item?.name || "Item",
        itemCode: l.item?.code || "",
        expectedQty: Number(l.quantity || 0),
        receivedQty: Number(l.quantity || 0),
      }))
    });
    setCollectorName("");
  }

  async function handleConfirmReturn(e) {
    e.preventDefault();
    if (!collectorName.trim() || !confirmModal) {
      showAlert({ title: "Collector Required", message: "Please enter the handler's name.", type: "warning" });
      return;
    }
    setConfirming(true);
    try {
      const confirmedLines = confirmModal.lines.map(l => ({
        itemId: l.itemId,
        receivedQuantity: Number(l.receivedQty || 0),
      }));
      await confirmReturn(confirmModal.returnObj.id, confirmedLines);
      setConfirmModal(null);
      await refreshItems();
      showAlert({ title: "Return Confirmed", message: `Stock return ${confirmModal.returnObj.returnNo} confirmed and received into Central Store.`, type: "success" });
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
          title="Stock Returns to Central Store"
          actions={isSite ? [
            {
              label: "Initiate Stock Return",
              icon: <RotateCcw size={16} />,
              variant: "primary",
              onClick: openInitiateModal,
            }
          ] : []}
          status={{
            label: `${awaitingCount} awaiting confirmation`,
            variant: awaitingCount > 0 ? "warning" : "success",
          }}
        />

        <table className="table">
          <thead>
            <tr>
              <th>Return No</th>
              <th>Project / Store</th>
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
                  {r.original?.lines?.map(l => `${l.item?.name || 'Item'} × ${l.quantity}`).join(", ") || "—"}
                </td>
                <td>
                  <Badge type={r.status === "Confirmed" ? "success" : "warning"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Awaiting Confirmation" && (isCentral || isAdmin) ? (
                    <button className="ch-btn ch-btn--success" onClick={() => openConfirmModal(r)}>
                      <CheckCircle2 size={16} />
                      Confirm Receipt
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
          <div className="app-modal" style={{ maxWidth: 680, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Initiate Return to Central Store</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Return unused physical stock from your site store inventory back to Central Store.
            </p>

            <div style={{ display: "flex", gap: 12, marginBottom: 16 }}>
              <button
                type="button"
                className={`btn ${returnSource === "inventory" ? "btn-primary" : "btn-outline"}`}
                onClick={() => setReturnSource("inventory")}
                style={{ fontSize: 13 }}
              >
                <Package size={14} style={{ marginRight: 6 }} />
                From Physical Site Inventory
              </button>
              {completedRequests.length > 0 && (
                <button
                  type="button"
                  className={`btn ${returnSource === "request" ? "btn-primary" : "btn-outline"}`}
                  onClick={() => setReturnSource("request")}
                  style={{ fontSize: 13 }}
                >
                  From Received Request
                </button>
              )}
            </div>

            {returnSource === "request" && (
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 6 }}>Select Material Request</label>
                <select
                  className="input"
                  value={selectedRequestId}
                  onChange={e => setSelectedRequestId(e.target.value)}
                >
                  {completedRequests.map(r => (
                    <option key={r.id} value={r.id}>{r.requestNo} — {r.project}</option>
                  ))}
                </select>
              </div>
            )}

            <form onSubmit={handleInitiateReturn}>
              <table className="table" style={{ marginBottom: 16 }}>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Available at Site</th>
                    <th>Return Quantity</th>
                    <th>Condition</th>
                  </tr>
                </thead>
                <tbody>
                  {returnLines.map((line, idx) => (
                    <tr key={idx}>
                      <td>
                        <div style={{ fontWeight: 500 }}>{line.itemName}</div>
                        {line.itemCode && <div style={{ fontSize: 12, color: "#64748b" }}>{line.itemCode}</div>}
                      </td>
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
                          placeholder="0"
                          style={{ width: 90 }}
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

                  {returnLines.length === 0 && (
                    <tr>
                      <td colSpan={4} style={{ textAlign: "center", color: "#64748b", padding: 20 }}>
                        {loadingInv ? "Loading site store inventory…" : "No physical stock available to return at this site store."}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>

              <div style={{ background: "#fef3c7", border: "1px solid #fde68a", borderRadius: 6, padding: "10px 14px", fontSize: 13, color: "#92400e", marginBottom: 20, display: "flex", gap: 8, alignItems: "flex-start" }}>
                <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: 1 }} />
                Returned stock will be sent to Central Store for physical receipt confirmation. Any quantity variance will create an automatic discrepancy log.
              </div>

              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" disabled={initiating} onClick={() => setInitiateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={initiating || returnLines.length === 0}>
                  {initiating ? "Submitting…" : "Submit Stock Return"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Central Manager: Per-Line Confirm Return Modal */}
      {confirmModal && (
        <div className="app-modal-backdrop">
          <div className="app-modal" style={{ maxWidth: 620, padding: 28, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Confirm Receipt — {confirmModal.returnObj.returnNo}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Inspect and enter the actual physical quantities received back at Central Store. Any variance from the expected return will automatically generate a discrepancy.
            </p>
            <form onSubmit={handleConfirmReturn}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 6 }}>Receiving Handler / Person Name</label>
                <input
                  className="input"
                  placeholder="Name of person receiving return at Central Store"
                  value={collectorName}
                  onChange={e => setCollectorName(e.target.value)}
                  required
                  autoFocus
                />
              </div>

              <div style={{ maxHeight: 300, overflowY: "auto", marginBottom: 20 }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Item</th>
                      <th style={{ textAlign: "right" }}>Expected Return</th>
                      <th style={{ textAlign: "right" }}>Actual Received Qty</th>
                    </tr>
                  </thead>
                  <tbody>
                    {confirmModal.lines.map((l, idx) => {
                      const diff = Number(l.receivedQty) - Number(l.expectedQty);
                      return (
                        <tr key={idx}>
                          <td style={{ fontWeight: 500 }}>
                            {l.itemName}
                            {l.itemCode && <span style={{ fontSize: 12, color: "#64748b", marginLeft: 6 }}>({l.itemCode})</span>}
                          </td>
                          <td style={{ textAlign: "right", color: "#475569" }}>{l.expectedQty}</td>
                          <td style={{ textAlign: "right" }}>
                            <input
                              type="number"
                              className="input"
                              min="0"
                              step="any"
                              style={{ width: 90, textAlign: "right", padding: "4px 8px", fontSize: 13 }}
                              value={l.receivedQty}
                              onChange={e => {
                                const updated = [...confirmModal.lines];
                                updated[idx] = { ...updated[idx], receivedQty: e.target.value };
                                setConfirmModal(c => ({ ...c, lines: updated }));
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

              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setConfirmModal(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-success" disabled={!collectorName.trim() || confirming}>
                  <CheckCircle2 size={16} />
                  {confirming ? "Confirming…" : "Confirm Receipt into Central Store"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
