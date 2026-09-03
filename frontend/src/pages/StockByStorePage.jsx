import { useEffect, useMemo, useState } from "react";
import CardHeader from "../components/CardHeader";
import { apiFetch } from "../api";
import { Search } from "lucide-react";

// Cross-store view for Central/Admin: "how much of item X do we have, and
// where is it" — one row per item, one column per store, instead of the
// Items page's Site tab, which only ever shows one store at a time.
//
// Data source: GET /api/inventory/site-inventory with no storeId param.
// That already returns every store's stock row for every item (Admin/
// Central are permitted to call it without a storeId) — this page is
// purely a client-side pivot of that same data, no new backend endpoint.
export default function StockByStorePage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState("");
  const [lowStockThreshold, setLowStockThreshold] = useState(5);

  useEffect(() => {
    setLoading(true);
    apiFetch("/api/inventory/site-inventory")
      .then((res) => setRows(Array.isArray(res) ? res : []))
      .catch((err) => setError(err?.message || "Failed to load stock."))
      .finally(() => setLoading(false));
  }, []);

  const { storeNames, itemMatrix } = useMemo(() => {
    const storeSet = new Set();
    const byItem = new Map();

    for (const r of rows) {
      if (!r) continue;
      storeSet.add(r.storeName);
      const key = r.itemCode || r.itemName;
      if (!byItem.has(key)) {
        byItem.set(key, {
          itemName: r.itemName,
          itemCode: r.itemCode,
          category: r.category,
          byStore: {},
          total: 0,
        });
      }
      const entry = byItem.get(key);
      const onHand = Number(r.onHand) || 0;
      entry.byStore[r.storeName] = (entry.byStore[r.storeName] || 0) + onHand;
      entry.total += onHand;
    }

    const storeNames = Array.from(storeSet).sort();
    let itemMatrix = Array.from(byItem.values()).sort((a, b) =>
      (a.itemName || "").localeCompare(b.itemName || "")
    );

    if (search.trim()) {
      const q = search.trim().toLowerCase();
      itemMatrix = itemMatrix.filter(
        (i) =>
          i.itemName?.toLowerCase().includes(q) ||
          i.itemCode?.toLowerCase().includes(q) ||
          i.category?.toLowerCase().includes(q)
      );
    }

    return { storeNames, itemMatrix };
  }, [rows, search]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Stock by Store"
          subtitle="Every item, broken down by which store currently holds it — Central/Admin view only."
          badge={`${itemMatrix.length} items`}
        />

        <div style={{ display: "flex", gap: 12, alignItems: "center", margin: "12px 0 16px" }}>
          <div style={{ position: "relative", flex: 1, maxWidth: 360 }}>
            <Search
              size={16}
              style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)", color: "#94a3b8" }}
            />
            <input
              className="input"
              style={{ paddingLeft: 32 }}
              placeholder="Search item name, code, or category..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <label style={{ fontSize: 13, color: "#64748b", display: "flex", alignItems: "center", gap: 6 }}>
            Low-stock highlight below
            <input
              type="number"
              className="input"
              style={{ width: 70 }}
              min={0}
              value={lowStockThreshold}
              onChange={(e) => setLowStockThreshold(Number(e.target.value) || 0)}
            />
          </label>
        </div>

        {error && <p style={{ color: "#dc2626" }}>{error}</p>}
        {loading && <p style={{ color: "#64748b" }}>Loading stock across all stores...</p>}

        {!loading && !error && itemMatrix.length === 0 && (
          <p style={{ color: "#64748b" }}>No stock records match your search.</p>
        )}

        {!loading && !error && itemMatrix.length > 0 && (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th style={{ position: "sticky", left: 0, background: "#fff" }}>Item</th>
                  <th>Code</th>
                  {storeNames.map((s) => (
                    <th key={s} style={{ textAlign: "right" }}>{s}</th>
                  ))}
                  <th style={{ textAlign: "right" }}>Total</th>
                </tr>
              </thead>
              <tbody>
                {itemMatrix.map((item) => (
                  <tr key={item.itemCode || item.itemName}>
                    <td style={{ position: "sticky", left: 0, background: "#fff", fontWeight: 500 }}>
                      {item.itemName}
                    </td>
                    <td style={{ color: "#64748b" }}>{item.itemCode}</td>
                    {storeNames.map((s) => {
                      const qty = item.byStore[s] ?? 0;
                      const isLow = qty > 0 && qty <= lowStockThreshold;
                      const isZero = qty === 0;
                      return (
                        <td
                          key={s}
                          style={{
                            textAlign: "right",
                            color: isZero ? "#cbd5e1" : isLow ? "#dc2626" : "#0f172a",
                            fontWeight: isLow ? 600 : 400,
                          }}
                        >
                          {qty}
                        </td>
                      );
                    })}
                    <td style={{ textAlign: "right", fontWeight: 600 }}>{item.total}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
