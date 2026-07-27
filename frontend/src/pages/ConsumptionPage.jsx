import { useMemo, useState, useEffect } from "react";
import Badge from "../components/Badge";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { Package, Flame, Search } from "lucide-react";
import { apiFetch } from "../api";

export default function ConsumptionPage() {
  const { defaultStoreId, consumeItems, stores } = useAppData();
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
  const [consumeInvRow, setConsumeInvRow] = useState(null);
  const [consumeQty, setConsumeQty] = useState("");
  const [consumeNote, setConsumeNote] = useState("");
  const [consumedAt, setConsumedAt] = useState(() => new Date().toISOString().slice(0, 10));
  
  const [siteInventory, setSiteInventory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  // Fetch site store physical inventory
  const fetchSiteInventory = () => {
    if (!selectedStoreId) return;
    setLoading(true);
    apiFetch(`/api/inventory/site-inventory?storeId=${selectedStoreId}`)
      .then((res) => {
        setSiteInventory(Array.isArray(res) ? res : []);
      })
      .catch((err) => console.error("Failed to fetch site inventory:", err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchSiteInventory();
  }, [selectedStoreId]);

  function openConsumeModal(invRow) {
    setConsumeInvRow(invRow);
    setConsumeQty("");
    setConsumeNote("");
    setConsumedAt(new Date().toISOString().slice(0, 10));
    setConsumeModalOpen(true);
  }

  async function handleConsume(e) {
    e.preventDefault();
    if (!consumeInvRow || !consumeQty || busy) return;
    setBusy(true);
    const qty = Number(consumeQty);
    if (isNaN(qty) || qty <= 0) {
      showAlert({ title: "Invalid Quantity", message: "Please enter a valid positive quantity.", type: "warning" });
      setBusy(false);
      return;
    }
    if (qty > consumeInvRow.available) {
      showAlert({ title: "Insufficient Stock", message: `Cannot consume more than ${consumeInvRow.available} available units at this store.`, type: "danger" });
      setBusy(false);
      return;
    }
    try {
      await consumeItems(selectedStoreId, [{
        itemId: consumeInvRow.itemId,
        quantity: qty,
        consumedAt,
        notes: consumeNote || null,
      }]);
      setConsumeModalOpen(false);
      setConsumeInvRow(null);
      showAlert({ title: "Success", message: `Successfully logged consumption of ${qty} × ${consumeInvRow.itemName}.`, type: "success" });
      fetchSiteInventory();
    } catch (err) {
      console.error(err);
      showAlert({ title: "Error", message: "Failed to consume item. " + (err?.message || "Unknown error"), type: "danger" });
    } finally {
      setBusy(false);
    }
  }

  const visibleInventory = useMemo(() => {
    return siteInventory.filter((inv) => {
      const matchesSearch =
        !search ||
        inv.itemName.toLowerCase().includes(search.toLowerCase()) ||
        inv.itemCode.toLowerCase().includes(search.toLowerCase());
      return matchesSearch;
    });
  }, [siteInventory, search]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Site Item Consumption"
          badge={`${visibleInventory.length} site inventory records`}
          icon={<Flame size={20} />}
          subtitle="Log utilization of physical items received at your site store."
        />

        <div className="filters" style={{ padding: "16px", borderBottom: "1px solid #f1f5f9", display: "flex", gap: "16px", alignItems: "center" }}>
          <div style={{ minWidth: "280px" }}>
            <select
              className="input"
              value={selectedStoreId || ""}
              onChange={(e) => setSelectedStoreId(e.target.value)}
            >
              <option value="" disabled>Select Site Store...</option>
              {availableStores.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.type})</option>
              ))}
            </select>
          </div>
          <div style={{ position: "relative", maxWidth: "400px", flex: 1 }}>
            <Search size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "#94a3b8" }} />
            <input
              className="input"
              placeholder="Search site stock items..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ paddingLeft: 36, width: "100%" }}
            />
          </div>
        </div>

        {loading && (
          <div style={{ padding: "12px 16px", fontSize: 13, color: "#64748b" }}>
            Loading site store inventory…
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Category</th>
              <th>On-Hand</th>
              <th>Available</th>
              <th>Consumed</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {visibleInventory.map((inv) => (
              <tr key={inv.id}>
                <td>
                  <div className="item-cell">
                    <span className="item-icon">
                      <Package size={18} />
                    </span>
                    <div>
                      <div className="item-name">{inv.itemName}</div>
                      <div className="item-code">{inv.itemCode} • <span style={{ color: "#2563eb", fontWeight: 500 }}>{inv.storeName}</span></div>
                    </div>
                  </div>
                </td>
                <td>{inv.category ? inv.category.replace(/_/g, " ") : "—"}</td>
                <td style={{ fontWeight: 600 }}>{inv.onHand} {inv.unitOfMeasure || ""}</td>
                <td>
                  <Badge type={Number(inv.available) > 0 ? "success" : "default"}>{inv.available} {inv.unitOfMeasure || ""}</Badge>
                </td>
                <td style={{ color: "#475569" }}>{inv.consumed}</td>
                <td>
                  <button
                    type="button"
                    className="ch-btn ch-btn--primary"
                    disabled={Number(inv.available) <= 0}
                    onClick={() => openConsumeModal(inv)}
                    style={Number(inv.available) <= 0 ? { opacity: 0.5, cursor: "not-allowed" } : undefined}
                    title={Number(inv.available) <= 0 ? "No available stock at this site store" : "Log item consumption"}
                  >
                    <Flame size={14} />
                    Consume
                  </button>
                </td>
              </tr>
            ))}
            {visibleInventory.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b", padding: "32px 16px" }}>
                  {loading
                    ? "Loading site store inventory…"
                    : search
                      ? "No site items match your search."
                      : "No physical inventory found at this site store. Items will appear here once received on a Material Request."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Consume Modal */}
      {consumeModalOpen && consumeInvRow && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh" }}>
          <div className="app-modal" style={{ maxWidth: 420, padding: 24, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Consume {consumeInvRow.itemName}</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Log stock usage for <strong>{consumeInvRow.storeName}</strong> ({consumeInvRow.itemCode}).
            </p>
            <form onSubmit={handleConsume}>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Available Stock</label>
                <div style={{ fontWeight: 600, fontSize: 15, color: "#16a34a" }}>
                  {consumeInvRow.available} {consumeInvRow.unitOfMeasure || "units"}
                </div>
              </div>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Quantity Consumed</label>
                <input
                  type="number"
                  className="input"
                  min="0.01"
                  max={consumeInvRow.available}
                  step="any"
                  value={consumeQty}
                  onChange={(e) => setConsumeQty(e.target.value)}
                  placeholder="Enter quantity"
                  required
                  autoFocus
                />
              </div>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Date</label>
                <input
                  type="date"
                  className="input"
                  value={consumedAt}
                  onChange={(e) => setConsumedAt(e.target.value)}
                  required
                />
              </div>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Notes (Optional)</label>
                <input
                  className="input"
                  value={consumeNote}
                  onChange={(e) => setConsumeNote(e.target.value)}
                  placeholder="Reason / utilization details"
                />
              </div>
              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setConsumeModalOpen(false)} disabled={busy}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  <Flame size={15} />
                  {busy ? "Saving…" : "Log Consumption"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
