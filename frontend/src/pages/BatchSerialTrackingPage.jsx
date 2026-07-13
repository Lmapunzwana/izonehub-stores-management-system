import { useState } from "react";
import { Boxes, Search } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

const STATUS_TYPE = {
  "In Stock": "success",
  "Issued to Project": "info",
  "Returned to Store": "warning",
};

export default function BatchSerialTrackingPage() {
  const { batches } = useAppData();
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState(null);

  const visible = batches.filter(
    (b) =>
      !search ||
      b.batchNo.toLowerCase().includes(search.toLowerCase()) ||
      b.item.toLowerCase().includes(search.toLowerCase()) ||
      b.serials.some((s) => s.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<Boxes size={20} />}
          title="Batch / Serial Tracking"
          subtitle="Full traceability from supplier receipt through to project issue and return"
          badge={`${batches.length} batches`}
        />
        <div className="filters">
          <input
            className="input"
            placeholder="Search batch, item, or serial number"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button className="ch-btn ch-btn--outline">
            <Search size={16} />
            Search
          </button>
        </div>

        <table className="table">
          <thead>
            <tr>
              <th>Batch No</th>
              <th>Item</th>
              <th>Serial Numbers</th>
              <th>Received Via</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {visible.map((b) => (
              <tr key={b.batchNo}>
                <td>{b.batchNo}</td>
                <td>{b.item}</td>
                <td>{b.serials.length} units</td>
                <td>{b.receivedVia}</td>
                <td>
                  <Badge type={STATUS_TYPE[b.status] || "default"}>{b.status}</Badge>
                </td>
                <td>
                  <button
                    className="btn"
                    onClick={() => setSelected(selected === b.batchNo ? null : b.batchNo)}
                  >
                    {selected === b.batchNo ? "Hide" : "Trace"}
                  </button>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No batches match your search.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {selected &&
        batches
          .filter((b) => b.batchNo === selected)
          .map((b) => (
            <div className="card" key={b.batchNo}>
              <CardHeader title={`Traceability — ${b.batchNo}`} />
              <div className="trace-chain" style={{ marginBottom: 16 }}>
                <span className="trace-node">Supplier GRN: {b.receivedVia}</span>
                <span className="trace-arrow">→</span>
                <span className="trace-node">{b.item}</span>
                <span className="trace-arrow">→</span>
                <span className="trace-node">{b.project}</span>
                <span className="trace-arrow">→</span>
                <span className="trace-node">
                  <Badge type={STATUS_TYPE[b.status] || "default"}>{b.status}</Badge>
                </span>
              </div>
              <h2 className="card-title" style={{ fontSize: 16 }}>Serial Numbers</h2>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                {b.serials.map((s) => (
                  <span key={s} className="badge info">
                    {s}
                  </span>
                ))}
              </div>
            </div>
          ))}
    </div>
  );
}
