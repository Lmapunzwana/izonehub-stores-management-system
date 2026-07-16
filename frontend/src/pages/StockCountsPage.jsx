import { useState, useMemo } from "react";
import {
  ClipboardList, Plus, CheckCircle, TrendingDown, TrendingUp,
  FileText, Search, Filter, RefreshCw, AlertTriangle, CheckCircle2,
} from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { apiFetch } from "../api";

const STATUS_TYPE = {
  Matched:     "success",
  Variance:    "warning",
  Shortage:    "danger",
  Surplus:     "warning",
  Uncounted:   "default",
  Adjusted:    "info",
};

function getLineStatus(row) {
  if (row.countedVal === undefined || row.countedVal === "") return "Uncounted";
  const variance = Number(row.countedVal) - Number(row.expected);
  if (variance === 0) return "Matched";
  if (variance < 0)  return "Shortage";
  return "Surplus";
}

export default function StockCountsPage() {
  const { stockCounts, initiateStockCount, submitCount, postStockCount, stores, defaultStoreId, user } = useAppData();

  const isSite    = user?.roles?.includes("SITE_STORE_MANAGER");
  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isAdmin   = user?.roles?.includes("SYSTEM_ADMINISTRATOR");

  // Site manager is locked to their own store
  const activeStores = stores.filter(s => s.active);
  const [storeId, setStoreId] = useState(
    isSite ? (user?.assignedStoreId || defaultStoreId || "") : (defaultStoreId || "")
  );

  const [initiating, setInitiating] = useState(false);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("All");

  function downloadFile(content, filename, type) {
    const url = URL.createObjectURL(content instanceof Blob ? content : new Blob([content], { type }));
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function onInitiate() {
    if (!storeId) {
      setError("Please select a store before initiating a count.");
      return;
    }
    setError(null);
    setInitiating(true);
    try {
      const count = await initiateStockCount(storeId);
      if (count?.id) {
        // Download count sheet PDF
        try {
          const blob = await apiFetch(`/api/stock-counts/${count.id}/full-audit-pdf`);
          downloadFile(blob, `stock-count-sheet-${count.id}.pdf`, "application/pdf");
        } catch (pdfErr) {
          console.warn("Count sheet PDF not yet available:", pdfErr?.message);
        }
      }
    } catch (e) {
      setError(e?.message || "Failed to initiate stock count.");
    } finally {
      setInitiating(false);
    }
  }

  const storeName = (id) => stores.find(s => s.id === id)?.name || "—";

  // The most recently initiated / in-progress count is shown first
  const sortedCounts = useMemo(() => [...stockCounts].reverse(), [stockCounts]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<ClipboardList size={20} />}
          title="Stock Counts"
          subtitle="Freeze a snapshot, capture physical counts, and post variances as controlled adjustments"
          actions={[
            {
              label: initiating ? "Initiating…" : "Initiate New Count",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: onInitiate,
              disabled: initiating || !storeId,
            },
          ]}
        />

        {error && (
          <div style={{
            padding: "10px 14px", background: "#fee2e2", color: "#b91c1c",
            borderRadius: 6, margin: "0 0 16px 0", fontSize: 13,
            display: "flex", gap: 8, alignItems: "center",
          }}>
            <AlertTriangle size={14} /> {error}
          </div>
        )}

        {/* Store selector */}
        <div className="form-grid" style={{ marginBottom: 20, maxWidth: 420 }}>
          <div>
            <label>Store</label>
            {isSite ? (
              <div style={{
                padding: "10px 14px", background: "#f8fafc", border: "1px solid #e2e8f0",
                borderRadius: 8, color: "#475569", display: "flex", alignItems: "center", justifyContent: "space-between",
              }}>
                <span style={{ fontWeight: 600 }}>{storeName(storeId)}</span>
                <span style={{ fontSize: 12, color: "#94a3b8" }}>locked to your store</span>
              </div>
            ) : (
              <select className="input" value={storeId} onChange={e => setStoreId(e.target.value)}>
                <option value="">Select a store…</option>
                {activeStores.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.type === "CENTRAL" ? "Central" : "Site"})
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>

        {stockCounts.length === 0 ? (
          <div style={{
            textAlign: "center", padding: "48px 24px", color: "#94a3b8",
          }}>
            <ClipboardList size={40} style={{ opacity: 0.3, marginBottom: 12 }} />
            <p style={{ margin: 0, fontWeight: 500 }}>No stock counts yet.</p>
            <p style={{ margin: "4px 0 0", fontSize: 13 }}>
              Initiate one above to freeze a snapshot of your current inventory.
            </p>
          </div>
        ) : null}

        {/* Count sessions */}
        {sortedCounts.map(count => (
          <CountSession
            key={count.id}
            count={count}
            stores={stores}
            submitCount={submitCount}
            postStockCount={postStockCount}
            downloadFile={downloadFile}
            isSite={isSite}
            isCentral={isCentral}
            isAdmin={isAdmin}
          />
        ))}
      </div>
    </div>
  );
}

// ── Individual count session component ────────────────────────────────────────

function CountSession({ count, stores, submitCount, postStockCount, downloadFile, isSite, isCentral, isAdmin }) {
  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("All");
  const [posting, setPosting] = useState(false);
  const [postError, setPostError] = useState(null);

  const storeName = (id) => stores.find(s => s.id === id)?.name || count.scope || "—";

  // Build enriched rows
  const rows = useMemo(() => count.snapshot.map(row => {
    const countedVal = count.counted[row.code];
    const variance = countedVal !== undefined ? Number(countedVal) - Number(row.expected) : null;
    const status = countedVal === undefined ? "Uncounted" : variance === 0 ? "Matched" : variance < 0 ? "Shortage" : "Surplus";
    return { ...row, countedVal, variance, status };
  }), [count]);

  // Overview stats
  const totalSKUs   = rows.length;
  const counted     = rows.filter(r => r.countedVal !== undefined).length;
  const matched     = rows.filter(r => r.status === "Matched").length;
  const variances   = rows.filter(r => r.status === "Shortage" || r.status === "Surplus").length;

  // Filtered visible rows
  const visible = useMemo(() => rows.filter(row => {
    const matchesSearch = !search ||
      row.name.toLowerCase().includes(search.toLowerCase()) ||
      row.code.toLowerCase().includes(search.toLowerCase());
    const matchesStatus = filterStatus === "All" || row.status === filterStatus;
    return matchesSearch && matchesStatus;
  }), [rows, search, filterStatus]);

  async function handlePost() {
    setPosting(true);
    setPostError(null);
    try {
      await postStockCount(count.id);
    } catch (e) {
      setPostError(e?.message || "Failed to post stock count.");
    } finally {
      setPosting(false);
    }
  }

  const isPosted = count.status === "Posted";
  const allCounted = rows.every(r => r.countedVal !== undefined);
  const anyVariance = variances > 0;

  return (
    <div style={{
      border: "1px solid #e2e8f0", borderRadius: 12, marginBottom: 24,
      overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.06)",
    }}>
      {/* Session header */}
      <div style={{
        background: isPosted ? "#f0fdf4" : "#fffbeb",
        borderBottom: "1px solid #e2e8f0",
        padding: "16px 20px",
        display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, flex: 1 }}>
          <ClipboardList size={18} style={{ color: isPosted ? "#16a34a" : "#d97706" }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, color: "#1e293b" }}>
              {count.countNo}
            </div>
            <div style={{ fontSize: 12, color: "#64748b" }}>
              {storeName(count.original?.store?.id)} · Started {count.date}
            </div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <span className={`badge ${isPosted ? "success" : "warning"}`}>
            {isPosted ? "✓ Posted" : "⏳ In Progress"}
          </span>
          {isPosted && (
            <button
              className="ch-btn ch-btn--outline"
              style={{ padding: "4px 12px", fontSize: 12 }}
              onClick={async () => {
                try {
                  const blob = await apiFetch(`/api/stock-counts/${count.original.id}/audit-report`);
                  downloadFile(blob, `stock-count-report-${count.original.id}.pdf`, "application/pdf");
                } catch (e) { console.error(e); }
              }}
            >
              <FileText size={12} /> Download Report
            </button>
          )}
          {!isPosted && (
            <button
              className="ch-btn ch-btn--outline"
              style={{ padding: "4px 12px", fontSize: 12 }}
              onClick={async () => {
                try {
                  const blob = await apiFetch(`/api/stock-counts/${count.original.id}/full-audit-pdf`);
                  downloadFile(blob, `count-sheet-${count.original.id}.pdf`, "application/pdf");
                } catch (e) { console.error(e); }
              }}
            >
              <FileText size={12} /> Export PDF
            </button>
          )}
        </div>
      </div>

      {/* Overview stat cards */}
      <div style={{ display: "flex", gap: 1, background: "#f1f5f9", borderBottom: "1px solid #e2e8f0" }}>
        {[
          { label: "Total SKUs", value: totalSKUs, icon: "📦", color: "#3b82f6" },
          { label: "Counted", value: counted, icon: "✅", color: "#8b5cf6" },
          { label: "Matching", value: matched, icon: "✔", color: "#10b981" },
          { label: "Variance", value: variances, icon: "⚠", color: variances > 0 ? "#f59e0b" : "#10b981" },
        ].map(card => (
          <div key={card.label} style={{
            flex: 1, padding: "16px 20px", background: "#fff", textAlign: "center",
          }}>
            <div style={{ fontSize: 20, marginBottom: 4 }}>{card.icon}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: card.color }}>{card.value}</div>
            <div style={{ fontSize: 12, color: "#94a3b8", fontWeight: 500 }}>{card.label}</div>
          </div>
        ))}
      </div>

      {/* Progress bar */}
      {!isPosted && totalSKUs > 0 && (
        <div style={{ padding: "10px 20px", borderBottom: "1px solid #f1f5f9", background: "#fafafa" }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6, fontSize: 12, color: "#64748b" }}>
            <span>Counting progress</span>
            <span><strong>{counted}/{totalSKUs}</strong> items counted</span>
          </div>
          <div style={{ height: 6, background: "#e2e8f0", borderRadius: 9999, overflow: "hidden" }}>
            <div style={{
              height: "100%",
              width: `${totalSKUs > 0 ? (counted / totalSKUs) * 100 : 0}%`,
              background: allCounted ? "#10b981" : "linear-gradient(90deg, #3b82f6, #8b5cf6)",
              borderRadius: 9999,
              transition: "width 0.3s ease",
            }} />
          </div>
        </div>
      )}

      {/* Search + Filter */}
      <div style={{ padding: "14px 20px", borderBottom: "1px solid #f1f5f9", display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
        <div style={{ position: "relative", flex: 1, minWidth: 200 }}>
          <Search size={14} style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)", color: "#94a3b8" }} />
          <input
            className="input"
            placeholder="Search items by name or SKU…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ paddingLeft: 32, fontSize: 13 }}
          />
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          {["All", "Uncounted", "Matched", "Shortage", "Surplus"].map(s => (
            <button
              key={s}
              className={`ch-btn ${filterStatus === s ? "ch-btn--primary" : "ch-btn--outline"}`}
              style={{ padding: "4px 10px", fontSize: 11, fontWeight: 500 }}
              onClick={() => setFilterStatus(s)}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Inventory reconciliation table */}
      <div style={{ overflowX: "auto" }}>
        <table className="table" style={{ marginBottom: 0 }}>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Item</th>
              <th style={{ textAlign: "right" }}>System Qty</th>
              <th style={{ textAlign: "right" }}>Physical Count</th>
              <th style={{ textAlign: "right" }}>Variance</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {visible.map(row => (
              <tr key={row.code} style={{
                background: row.status === "Shortage" ? "#fff5f5" :
                            row.status === "Surplus"  ? "#fffbeb" :
                            row.status === "Matched"  ? "#f0fdf4" : undefined,
              }}>
                <td style={{ fontSize: 12, color: "#64748b", fontFamily: "monospace" }}>
                  {row.code}
                </td>
                <td>
                  <div style={{ fontWeight: 500 }}>{row.name}</div>
                </td>
                <td style={{ textAlign: "right", fontWeight: 600 }}>
                  {Number(row.expected).toLocaleString()}
                </td>
                <td style={{ textAlign: "right" }}>
                  {isPosted ? (
                    <span style={{ fontWeight: 600 }}>
                      {row.countedVal !== undefined ? Number(row.countedVal).toLocaleString() : <span style={{ color: "#94a3b8" }}>—</span>}
                    </span>
                  ) : (
                    <input
                      className="input"
                      type="number"
                      min="0"
                      step="any"
                      placeholder={String(row.expected)}
                      value={row.countedVal ?? ""}
                      onChange={e => submitCount(count.id, row.code, e.target.value === "" ? undefined : Number(e.target.value))}
                      style={{ width: 90, textAlign: "right", padding: "4px 8px", fontSize: 13 }}
                    />
                  )}
                </td>
                <td style={{ textAlign: "right" }}>
                  {row.variance === null ? (
                    <span style={{ color: "#94a3b8" }}>—</span>
                  ) : row.variance === 0 ? (
                    <span style={{ color: "#10b981", fontWeight: 700 }}>✓ 0</span>
                  ) : (
                    <span style={{
                      color: row.variance < 0 ? "#dc2626" : "#d97706",
                      fontWeight: 700,
                      display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 4,
                    }}>
                      {row.variance < 0 ? <TrendingDown size={14} /> : <TrendingUp size={14} />}
                      {row.variance > 0 ? `+${row.variance}` : row.variance}
                    </span>
                  )}
                </td>
                <td>
                  <Badge type={STATUS_TYPE[row.status] || "default"}>
                    {row.status === "Matched"   ? "✅ Matched"  :
                     row.status === "Shortage"  ? "🔴 Shortage" :
                     row.status === "Surplus"   ? "🟡 Surplus"  :
                     row.status === "Uncounted" ? "⬜ Pending"  :
                     row.status}
                  </Badge>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#94a3b8", padding: "28px 16px" }}>
                  {search || filterStatus !== "All" ? "No items match the current filter." : "No items in this count."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Submit count / post adjustments */}
      {!isPosted && (isCentral || isAdmin) && (
        <div style={{ padding: "16px 20px", borderTop: "1px solid #f1f5f9", background: "#fafafa" }}>
          {postError && (
            <div style={{
              padding: "8px 12px", background: "#fee2e2", color: "#b91c1c",
              borderRadius: 6, marginBottom: 12, fontSize: 13,
            }}>
              {postError}
            </div>
          )}
          {anyVariance && (
            <div style={{
              padding: "10px 14px", background: "#fffbeb", border: "1px solid #fde68a",
              borderRadius: 6, marginBottom: 14, fontSize: 13, color: "#92400e",
              display: "flex", gap: 8, alignItems: "center",
            }}>
              <AlertTriangle size={14} />
              <span>
                <strong>{variances} variance{variances !== 1 ? "s" : ""}</strong> detected.
                Posting this count will create controlled adjustment transactions for all variances.
                This does NOT overwrite transactions — it creates new reconciliation records.
              </span>
            </div>
          )}
          <div style={{ display: "flex", gap: 12, alignItems: "center", justifyContent: "flex-end" }}>
            {!allCounted && (
              <span style={{ fontSize: 13, color: "#94a3b8" }}>
                {totalSKUs - counted} items still need physical counts
              </span>
            )}
            <button
              className="ch-btn ch-btn--success"
              disabled={posting}
              onClick={handlePost}
              style={{ display: "flex", alignItems: "center", gap: 6 }}
            >
              <CheckCircle size={16} />
              {posting ? "Posting…" : "Submit Count & Post Adjustments"}
            </button>
          </div>
        </div>
      )}

      {isPosted && (
        <div style={{
          padding: "14px 20px", borderTop: "1px solid #f1f5f9",
          background: "#f0fdf4", display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#15803d",
        }}>
          <CheckCircle2 size={16} />
          <span>Count finalized. All variances have been processed as inventory adjustments.</span>
        </div>
      )}
    </div>
  );
}
