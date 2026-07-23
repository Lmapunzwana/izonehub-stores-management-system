import { useState } from "react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { AlertTriangle } from "lucide-react";

const STATUS_TYPE = {
  OPEN: "danger",
  UNDER_INVESTIGATION: "warning",
  RESOLVED: "success",
};

function sourceRef(d) {
  if (d.grn) return `GRN ${d.grn.referenceNumber || d.grn.id?.substring(0, 8).toUpperCase()}`;
  if (d.receipt) return `MIV — ${d.receipt.materialRequest?.sourceStore?.name || "Dispatch"}`;
  if (d.stockReturn) return `Return RT-${d.stockReturn.id.substring(0, 8).toUpperCase()}`;
  if (d.stockCount) return `Stock Count SC-${d.stockCount.id?.substring(0, 8).toUpperCase()}${d.stockCount.store?.name ? ` (${d.stockCount.store.name})` : ""}`;
  return "—";
}

// GRN discrepancies: difference between expected (dispatched) and received.
// Transit/return discrepancies: what was dispatched vs received.
function isGrnSource(d) { return !!d.grn; }

export default function DiscrepanciesPage() {
  const { discrepancies, resolveDiscrepancy } = useAppData();
  const { showAlert } = useAppModal();
  const [notesById, setNotesById] = useState({});
  const [busyId, setBusyId] = useState(null);

  const openCount = discrepancies.filter(d => d.status === "OPEN").length;

  async function onResolve(d, recovered) {
    const notes = notesById[d.id]?.trim();
    if (!recovered && !notes) {
      showAlert({
        title: "Reason Required",
        message: "A write-off requires a mandatory reason. Please enter notes explaining the write-off.",
        type: "warning"
      });
      return;
    }
    setBusyId(d.id + (recovered ? "_r" : "_w"));
    try {
      await resolveDiscrepancy(d.id, recovered, notes);
    } catch (e) {
      showAlert({ title: "Error", message: e?.message || "Failed to resolve discrepancy.", type: "danger" });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Discrepancies"
          subtitle="Recovered stock is returned to on-hand; a write-off releases frozen stock permanently (mandatory reason required)."
          status={{ label: `${openCount} open`, variant: openCount > 0 ? "warning" : "success" }}
        />

        <table className="table">
          <thead>
            <tr>
              <th>Source</th>
              <th>Item</th>
              <th>{/* Column label adapts per row type */}Expected</th>
              <th>Received</th>
              <th>Frozen</th>
              <th>Status</th>
              <th>Resolution</th>
            </tr>
          </thead>
          <tbody>
            {discrepancies.map(d => {
              const isGrn = isGrnSource(d);
              const expectedLabel  = isGrn ? "Expected" : "Dispatched";
              const receivedLabel  = "Received";
              const busyR = busyId === d.id + "_r";
              const busyW = busyId === d.id + "_w";
              return (
                <tr key={d.id}>
                  <td style={{ fontSize: 13, color: "#64748b" }}>{sourceRef(d)}</td>
                  <td style={{ fontWeight: 600 }}>{d.item?.name || "—"}</td>
                  <td title={expectedLabel}>{d.dispatchedQuantity}</td>
                  <td title={receivedLabel}>{d.receivedQuantity}</td>
                  <td style={{ color: "#7c3aed", fontWeight: 600 }}>{d.frozenQuantity}</td>
                  <td>
                    <Badge type={STATUS_TYPE[d.status] || "default"}>{d.status}</Badge>
                  </td>
                  <td>
                    {d.status === "OPEN" ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 220 }}>
                        <textarea
                          className="input"
                          rows={2}
                          placeholder="Resolution notes (required for write-off)…"
                          value={notesById[d.id] || ""}
                          onChange={e => setNotesById({ ...notesById, [d.id]: e.target.value })}
                          style={{ fontSize: 12, padding: "6px 8px", resize: "vertical" }}
                        />
                        <div className="action-buttons">
                          <button
                            className="btn btn-warning"
                            disabled={busyW || busyR || !(notesById[d.id]?.trim())}
                            title={!(notesById[d.id]?.trim()) ? "Enter resolution notes to enable write-off" : "Write off frozen stock permanently"}
                            onClick={() => onResolve(d, false)}
                          >
                            {busyW ? "Writing…" : "Write Off"}
                          </button>
                          <button
                            className="btn btn-success"
                            disabled={busyR || busyW}
                            title="Return frozen stock to on-hand"
                            onClick={() => onResolve(d, true)}
                          >
                            {busyR ? "Recovering…" : "Mark Recovered"}
                          </button>
                        </div>
                        {!(notesById[d.id]?.trim()) && (
                          <div style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 11, color: "#92400e" }}>
                            <AlertTriangle size={11} /> Write-off requires notes
                          </div>
                        )}
                      </div>
                    ) : (
                      <div style={{ fontSize: 13, color: "#64748b", maxWidth: 200 }}>
                        <div style={{ fontWeight: 500, marginBottom: 2 }}>
                          {d.status === "RESOLVED" ? (d.recovered ? "Recovered" : "Written Off") : d.status}
                        </div>
                        {d.resolutionNotes && <div>{d.resolutionNotes}</div>}
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
            {discrepancies.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: "center", color: "#64748b", padding: "24px 0" }}>
                  No discrepancies recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
