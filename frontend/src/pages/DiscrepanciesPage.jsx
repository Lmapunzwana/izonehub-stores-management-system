import { useState } from "react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

// backend/.../movement/Discrepancy.java has no "ref"/"type"/"diff" fields —
// it links to either a GRN (receiving-side variance) or a Receipt (a
// material request's dispatch/receive variance), an item, and three
// quantities: dispatchedQuantity, receivedQuantity, frozenQuantity.
const STATUS_TYPE = {
  OPEN: "danger",
  UNDER_INVESTIGATION: "warning",
  RESOLVED: "success",
};

function sourceRef(d) {
  if (d.grn) return `GRN ${d.grn.referenceNumber}`;
  if (d.receipt) return `Dispatch on ${d.receipt.materialRequest?.sourceStore?.name || "request"}`;
  return "—";
}

export default function DiscrepanciesPage() {
  const { discrepancies, resolveDiscrepancy } = useAppData();
  const [notesById, setNotesById] = useState({});
  const openCount = discrepancies.filter((d) => d.status === "OPEN").length;

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Discrepancies"
          subtitle="Recovered stock is returned to on-hand; a write-off releases the frozen quantity permanently."
          status={{ label: `${openCount} open`, variant: openCount > 0 ? "warning" : "success" }}
        />
        <table className="table">
          <thead>
            <tr>
              <th>Source</th>
              <th>Item</th>
              <th>Dispatched</th>
              <th>Received</th>
              <th>Frozen</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {discrepancies.map((d) => (
              <tr key={d.id}>
                <td>{sourceRef(d)}</td>
                <td>{d.item?.name || "—"}</td>
                <td>{d.dispatchedQuantity}</td>
                <td>{d.receivedQuantity}</td>
                <td>{d.frozenQuantity}</td>
                <td>
                  <Badge type={STATUS_TYPE[d.status] || "default"}>{d.status}</Badge>
                </td>
                <td>
                  {d.status === "OPEN" ? (
                    <div className="action-buttons">
                      <button
                        className="btn btn-warning"
                        onClick={() => resolveDiscrepancy(d.id, false, notesById[d.id])}
                        title="Financial write-off — frozen stock is not returned to on-hand"
                      >
                        Write Off
                      </button>
                      <button
                        className="btn btn-success"
                        onClick={() => resolveDiscrepancy(d.id, true, notesById[d.id])}
                        title="Operational recovery — frozen stock is returned to on-hand"
                      >
                        Mark Recovered
                      </button>
                    </div>
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>
                      {d.resolutionNotes || "—"}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {discrepancies.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: "center", color: "#64748b" }}>
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
