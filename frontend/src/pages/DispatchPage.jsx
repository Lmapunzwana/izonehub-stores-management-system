import { useState } from "react";
import { Send, PackageCheck } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

// backend/.../movement/MaterialRequestStatus.java has no "Dispatched" or
// "Received" value — dispatch() moves a request straight to IN_TRANSIT
// ("In Transit"), and receive() resolves it to COMPLETED ("Received") or
// DISCREPANCY ("Received (Discrepancy)").
const STATUS_TYPE = {
  Approved: "info",
  "In Transit": "warning",
  Received: "success",
  "Received (Discrepancy)": "danger",
};

export default function DispatchPage() {
  const { materialRequests, dispatchRequest, markRequestReceived, initiateReturn } = useAppData();
  const { showAlert } = useAppModal();
  const [collector, setCollector] = useState({});
  const [returnModalOpen, setReturnModalOpen] = useState(false);
  const [returnRequest, setReturnRequest] = useState(null);
  const [returnLines, setReturnLines] = useState([]);
  
  const relevant = materialRequests.filter(
    (r) => r.status !== "Pending Approval" && r.status !== "Rejected" && r.status !== "Draft"
  );

  function openReturn(r) {
    setReturnRequest(r);
    // Initialize return lines with all received items, quantity 0, condition SERVICEABLE
    setReturnLines(
      r.original.lines.map((l) => ({
        itemId: l.item.id,
        itemName: l.item.name,
        receivedQuantity: l.receivedQuantity || l.dispatchedQuantity || 0, // Fallback if no specific received field
        quantity: 0,
        condition: "SERVICEABLE",
      }))
    );
    setReturnModalOpen(true);
  }

  async function handleReturn(e) {
    e.preventDefault();
    const payloadLines = returnLines
      .filter((l) => l.quantity > 0)
      .map(({ itemId, quantity, condition }) => ({ itemId, quantity: Number(quantity), condition }));
    
    if (payloadLines.length === 0) {
      showAlert({ title: "Validation Error", message: "Please enter a return quantity for at least one item.", type: "warning" });
      return;
    }

    try {
      await initiateReturn(returnRequest.id, payloadLines);
      setReturnModalOpen(false);
      setReturnRequest(null);
    } catch (err) {
      console.error(err);
      showAlert({ title: "Error", message: "Failed to initiate return. " + err.message, type: "danger" });
    }
  }

  function linesSummary(r) {
    return r.lines.map((l) => `${l.item} × ${l.requested}`).join(", ") || "—";
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<Send size={20} />}
          title="Issues &amp; Dispatch"
          subtitle="Approved requests move here for MIV creation and stock deduction"
          status={{
            label: `${materialRequests.filter((r) => r.status === "Approved").length} awaiting dispatch`,
            variant: "warning",
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
            {relevant.map((r) => (
              <tr key={r.requestNo}>
                <td>{r.requestNo}</td>
                <td>{r.project}</td>
                <td>{linesSummary(r)}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Approved" && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <input
                        className="input"
                        placeholder="Collector name"
                        style={{ width: 140 }}
                        value={collector[r.requestNo]?.name || ""}
                        onChange={(e) =>
                          setCollector((c) => ({
                            ...c,
                            [r.requestNo]: { ...c[r.requestNo], name: e.target.value },
                          }))
                        }
                      />
                      <button
                        className="ch-btn ch-btn--primary"
                        onClick={async () => {
                          await dispatchRequest(
                            r.requestNo,
                            collector[r.requestNo]?.name,
                            collector[r.requestNo]?.empId
                          );
                          window.open(`/api/material-requests/${r.id}/dispatch-note`, "_blank");
                        }}
                      >
                        <Send size={16} />
                        Create MIV & Dispatch
                      </button>
                    </div>
                  )}
                  {r.status === "In Transit" && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <button
                        className="ch-btn ch-btn--success"
                        onClick={async () => {
                          await markRequestReceived(r.requestNo);
                          window.open(`/api/material-requests/${r.id}/dispatch-note`, "_blank");
                        }}
                      >
                        <PackageCheck size={16} />
                        Mark Received
                      </button>
                      <button
                        className="ch-btn ch-btn--outline"
                        style={{ padding: "6px 12px", fontSize: "13px" }}
                        onClick={() => window.open(`/api/material-requests/${r.id}/dispatch-note`, "_blank")}
                      >
                        MIV PDF
                      </button>
                    </div>
                  )}
                  {(r.status === "Received" || r.status === "Received (Discrepancy)") && (
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span style={{ color: "#64748b", fontSize: 13 }}>Complete</span>
                      <button
                        className="ch-btn ch-btn--outline"
                        style={{ padding: "4px 8px", fontSize: "12px" }}
                        onClick={() => window.open(`/api/material-requests/${r.id}/dispatch-note`, "_blank")}
                      >
                        MIV PDF
                      </button>
                      <button
                        className="ch-btn ch-btn--primary"
                        style={{ padding: "4px 8px", fontSize: "12px" }}
                        onClick={() => openReturn(r)}
                      >
                        Return Items
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {relevant.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", color: "#64748b" }}>
                  Nothing to dispatch yet — approve a material request first.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {returnModalOpen && returnRequest && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: 600 }}>
            <h3 style={{ marginTop: 0 }}>Return Items (Request: {returnRequest.requestNo})</h3>
            <form onSubmit={handleReturn}>
              <table className="table" style={{ marginTop: 16 }}>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Max (Received)</th>
                    <th>Return Qty</th>
                    <th>Condition</th>
                  </tr>
                </thead>
                <tbody>
                  {returnLines.map((line, idx) => (
                    <tr key={line.itemId}>
                      <td>{line.itemName}</td>
                      <td>{line.receivedQuantity}</td>
                      <td>
                        <input
                          type="number"
                          className="input"
                          min="0"
                          max={line.receivedQuantity}
                          step="any"
                          value={line.quantity || ""}
                          onChange={(e) => {
                            const newLines = [...returnLines];
                            newLines[idx].quantity = e.target.value;
                            setReturnLines(newLines);
                          }}
                          style={{ width: 80 }}
                        />
                      </td>
                      <td>
                        <select
                          className="input"
                          value={line.condition}
                          onChange={(e) => {
                            const newLines = [...returnLines];
                            newLines[idx].condition = e.target.value;
                            setReturnLines(newLines);
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
              <div className="modal-actions" style={{ marginTop: 24, display: "flex", gap: 8, justifyContent: "flex-end" }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => setReturnModalOpen(false)}
                >
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
    </div>
  );
}
