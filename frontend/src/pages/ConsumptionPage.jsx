import { useMemo, useState, useEffect } from "react";
import Badge from "../components/Badge";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { Package, Flame, Search, Calendar } from "lucide-react";
import { apiFetch } from "../api";

export default function ConsumptionPage() {
  const { items, defaultStoreId, consumeItems, stores } = useAppData();
  const { showAlert } = useAppModal();
  const [search, setSearch] = useState("");
  const [selectedStoreId, setSelectedStoreId] = useState(defaultStoreId);

  const availableStores = useMemo(() => stores.filter(s => s.active && !s.closing), [stores]);

  useEffect(() => {
    if ((!selectedStoreId || !availableStores.some(s => s.id === selectedStoreId)) && availableStores.length > 0) {
      const siteStore = availableStores.find(s => s.type === "SITE") || availableStores[0];
      setSelectedStoreId(siteStore.id);
    } else if (!selectedStoreId && defaultStoreId) {
      setSelectedStoreId(defaultStoreId);
    }
  }, [availableStores, selectedStoreId, defaultStoreId]);

  const [consumeModalOpen, setConsumeModalOpen] = useState(false);
  const [consumeItem, setConsumeItem] = useState(null);
  const [consumeQty, setConsumeQty] = useState("");
  const [consumeNote, setConsumeNote] = useState("");
  // Default to today's date in YYYY-MM-DD format
  const [consumedAt, setConsumedAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [siteStock, setSiteStock] = useState({});
  const [loadingStock, setLoadingStock] = useState(false);
  const [busy, setBusy] = useState(false);

  // Fetch stock specific to the current site store
  useEffect(() => {
    if (!selectedStoreId) return;
    setLoadingStock(true);
    apiFetch(`/api/reports/current-stock?storeId=${selectedStoreId}`)
      .then((res) => {
        const rows = Array.isArray(res) ? res : res?.content || [];
        const stockMap = {};
        rows.forEach(r => {
          const avail = Math.max(0, Number(r.onHand || 0) - Number(r.reserved || 0));
          if (r.itemCode) stockMap[r.itemCode] = avail;
          if (r.itemId) stockMap[r.itemId] = avail;
        });
        setSiteStock(stockMap);
      })
      .catch(err => console.error("Failed to fetch site stock:", err))
      .finally(() => setLoadingStock(false));
  }, [selectedStoreId]);

  function openConsumeModal(item) {
    setConsumeItem(item);
    setConsumeQty("");
    setConsumeNote("");
    setConsumedAt(new Date().toISOString().slice(0, 10));
    setConsumeModalOpen(true);
  }

  async function handleConsume(e) {
    e.preventDefault();
    if (!consumeItem || !consumeQty || busy) return;
    setBusy(true);
    const qty = Number(consumeQty);
    if (isNaN(qty) || qty <= 0) {
      showAlert({ title: "Invalid Quantity", message: "Please enter a valid positive quantity.", type: "warning" });
      return;
    }
    if (qty > consumeItem.available) {
      showAlert({ title: "Insufficient Stock", message: `Cannot consume more than ${consumeItem.available} available units at this store.`, type: "danger" });
      return;
    }
    try {
      await consumeItems(selectedStoreId, [{
        itemId: consumeItem.id,
        quantity: qty,
        consumedAt,
        notes: consumeNote || null,
      }]);
      setConsumeModalOpen(false);
      setConsumeItem(null);
      showAlert({ title: "Success", message: `Successfully consumed ${qty} × ${consumeItem.name} on ${consumedAt}.`, type: "success" });

      // Refresh site stock after consumption
      apiFetch(`/api/reports/current-stock?storeId=${selectedStoreId}`)
        .then((res) => {
          const rows = Array.isArray(res) ? res : res?.content || [];
          const stockMap = {};
          rows.forEach(r => {
            const avail = Math.max(0, Number(r.onHand || 0) - Number(r.reserved || 0));
            if (r.itemCode) stockMap[r.itemCode] = avail;
            if (r.itemId) stockMap[r.itemId] = avail;
          });
          setSiteStock(stockMap);
        })
        .catch(console.error);

    } catch (err) {
      console.error(err);
      showAlert({ title: "Error", message: "Failed to consume item. " + (err?.message || "Unknown error"), type: "danger" });
    } finally {
      setBusy(false);
    }
  }

  const visibleItems = useMemo(() => {
    // Only show items that have actual physical stock > 0 AT THIS SPECIFIC SITE STORE
    return items
      .map(i => {
        const avail = (siteStock[i.code] !== undefined ? siteStock[i.code] : siteStock[i.id]) || 0;
        return { ...i, available: avail };
      })
      .filter((i) => {
        const hasStock = i.available > 0;
        const matchesSearch =
          !search ||
          i.name.toLowerCase().includes(search.toLowerCase()) ||
          i.code.toLowerCase().includes(search.toLowerCase());
        return hasStock && matchesSearch;
      });
  }, [items, search, siteStock]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Item Consumption"
          badge={`${visibleItems.length} items available`}
          icon={<Flame size={20} />}
          subtitle="Log items that have been utilized at this site. Select a date to record past consumption."
        />

        <div className="filters" style={{ padding: "16px", borderBottom: "1px solid #f1f5f9", display: "flex", gap: "16px", alignItems: "center" }}>
          <div style={{ minWidth: "280px" }}>
            <select
              className="input"
              value={selectedStoreId || ""}
              onChange={(e) => setSelectedStoreId(e.target.value)}
            >
              <option value="" disabled>Select Store...</option>
              {availableStores.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.type})</option>
              ))}
            </select>
          </div>
          <div style={{ position: "relative", maxWidth: "400px", flex: 1 }}>
            <Search size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "#94a3b8" }} />
            <input
              className="input"
              placeholder="Search available items..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ paddingLeft: 36, width: "100%" }}
            />
          </div>
        </div>

        {loadingStock && (
          <div style={{ padding: "12px 16px", fontSize: 13, color: "#64748b" }}>
            Loading current stock levels…
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Category</th>
              <th>Available (Physical)</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {visibleItems.map((item) => (
              <tr key={item.code}>
                <td>
                  <div className="item-cell">
                    <span className="item-icon">
                      <Package size={18} />
                    </span>
                    <div>
                      <div className="item-name">{item.name}</div>
                      <div className="item-code">{item.code}</div>
                    </div>
                  </div>
                </td>
                <td>{item.category ? item.category.replace(/_/g, " ") : "—"}</td>
                <td>
                  <Badge type="success">{item.available} {item.original?.unitOfMeasure || ""}</Badge>
                </td>
                <td>
                  <button
                    type="button"
                    className="ch-btn ch-btn--primary"
                    onClick={() => openConsumeModal(item)}
                    title="Log item consumption"
                  >
                    <Flame size={14} />
                    Consume
                  </button>
                </td>
              </tr>
            ))}
            {visibleItems.length === 0 && (
              <tr>
                <td colSpan={4} style={{ textAlign: "center", color: "#64748b", padding: "32px 16px" }}>
                  {loadingStock
                    ? "Loading…"
                    : search
                      ? "No items match your search."
                      : "You currently have no physical stock available to consume. Receive requested items first."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Consume Modal */}
      {consumeModalOpen && consumeItem && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh", overflowY: "auto" }}>
          <div className="app-modal" style={{ maxWidth: 440, padding: 28, textAlign: "left" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
              <span style={{
                width: 36, height: 36, borderRadius: 8,
                background: "linear-gradient(135deg,#f59e0b,#ef4444)",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>
                <Flame size={18} color="#fff" />
              </span>
              <div>
                <h3 style={{ margin: 0, fontSize: 16 }}>Consume {consumeItem.name}</h3>
                <div style={{ fontSize: 12, color: "#94a3b8" }}>{consumeItem.code}</div>
              </div>
            </div>

            <div style={{
              background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 6,
              padding: "10px 14px", marginBottom: 20, fontSize: 13, color: "#15803d",
            }}>
              Available in physical inventory: <strong>{consumeItem.available} {consumeItem.original?.unitOfMeasure || ""}</strong>
            </div>

            <form onSubmit={handleConsume}>
              <div className="form-group" style={{ marginBottom: 16 }}>
                <label style={{ display: "flex", alignItems: "center", gap: 6, fontWeight: 500, marginBottom: 6 }}>
                  <Calendar size={14} />
                  Consumption Date <span style={{ color: "#dc2626" }}>*</span>
                </label>
                <input
                  type="date"
                  className="input"
                  required
                  max={new Date().toISOString().slice(0, 10)}
                  value={consumedAt}
                  onChange={(e) => setConsumedAt(e.target.value)}
                />
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
                  Date when this material was actually used (can be a past date)
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 16 }}>
                <label style={{ fontWeight: 500, marginBottom: 6, display: "block" }}>
                  Quantity Consumed <span style={{ color: "#dc2626" }}>*</span>
                </label>
                <input
                  type="number"
                  className="input"
                  min="0.01"
                  max={consumeItem.available}
                  step="any"
                  required
                  value={consumeQty}
                  onChange={(e) => setConsumeQty(e.target.value)}
                  autoFocus
                  placeholder={`Max: ${consumeItem.available}`}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 20 }}>
                <label style={{ fontWeight: 500, marginBottom: 6, display: "block" }}>
                  Notes / Reason (optional)
                </label>
                <input
                  type="text"
                  className="input"
                  placeholder="e.g. Used in foundation pouring — Block C"
                  value={consumeNote}
                  onChange={(e) => setConsumeNote(e.target.value)}
                />
              </div>

              <div className="modal-actions" style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                <button
                  type="button"
                  className="ch-btn ch-btn--outline"
                  disabled={busy}
                  onClick={() => { setConsumeModalOpen(false); setConsumeItem(null); }}
                >
                  Cancel
                </button>
                <button type="submit" className="ch-btn ch-btn--primary" disabled={busy}>
                  <Flame size={14} />
                  {busy ? "Consuming…" : "Confirm Consumption"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
