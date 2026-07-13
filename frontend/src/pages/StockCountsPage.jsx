import { useState } from "react";
import { ClipboardList, Plus, CheckCircle } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

export default function StockCountsPage() {
  const { stockCounts, initiateStockCount, submitCount, postStockCount, stores, defaultStoreId } = useAppData();
  const activeStores = stores.filter((s) => s.active);
  const [storeId, setStoreId] = useState(defaultStoreId || "");

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<ClipboardList size={20} />}
          title="Stock Counts"
          subtitle="Freeze a snapshot, capture physical counts, and post variances"
          actions={[
            {
              label: "Initiate Count",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => initiateStockCount(storeId),
            },
          ]}
        />

        <div className="form-grid" style={{ marginBottom: 20 }}>
          <div>
            <label>Store</label>
            <div className="select-wrap">
              <select className="input" value={storeId} onChange={(e) => setStoreId(e.target.value)}>
                <option value="">Select a store…</option>
                {activeStores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.type === "CENTRAL" ? "Central" : "Site"})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {stockCounts.length === 0 && (
          <p style={{ color: "#64748b" }}>No stock counts yet. Initiate one to freeze a snapshot.</p>
        )}

        {[...stockCounts].reverse().map((count) => (
          <div key={count.id} className="sub-panel" style={{ marginBottom: 16 }}>
            <div className="sub-panel-header">
              <div className="sub-panel-header-left">
                <ClipboardList size={18} />
                {count.countNo} · {count.scope} · {count.date}
              </div>
              <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                <span className={`badge ${count.status === "Posted" ? "success" : "warning"}`}>
                  {count.status}
                </span>
                {count.status === "Posted" && (
                  <button 
                    className="ch-btn ch-btn--outline" 
                    style={{ padding: "4px 8px", fontSize: "12px" }}
                    onClick={() => window.open(`/api/stock-counts/${count.original.id}/audit-report`, "_blank")}
                  >
                    Download Audit Report
                  </button>
                )}
              </div>
            </div>
            <div style={{ padding: 16 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Expected (Snapshot)</th>
                    <th>Counted</th>
                    <th>Variance</th>
                  </tr>
                </thead>
                <tbody>
                  {count.snapshot.map((row) => {
                    const counted = count.counted[row.code];
                    const variance =
                      counted !== undefined ? Number(counted) - row.expected : null;
                    return (
                      <tr key={row.code}>
                        <td>{row.name}</td>
                        <td>{row.expected}</td>
                        <td>
                          {count.status === "Posted" ? (
                            counted ?? "—"
                          ) : (
                            <input
                              className="input"
                              type="number"
                              placeholder={String(row.expected)}
                              value={counted ?? ""}
                              onChange={(e) => submitCount(count.id, row.code, e.target.value)}
                            />
                          )}
                        </td>
                        <td>
                          {variance === null ? (
                            "—"
                          ) : (
                            <span className={variance === 0 ? "variance-ok" : "variance-bad"}>
                              {variance > 0 ? `+${variance}` : variance}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {count.status === "Counting" && (
                <div className="actions-row" style={{ marginTop: 16 }}>
                  <button
                    className="ch-btn ch-btn--success"
                    onClick={() => postStockCount(count.id)}
                  >
                    <CheckCircle size={16} />
                    Post Stock Adjustment
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
