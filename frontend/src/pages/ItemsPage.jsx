import { useMemo, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "../components/Badge";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import {
  Plus,
  Download,
  ChevronsUpDown,
  Filter,
  Package,
  ShoppingCart,
  Snowflake,
  Flame,
  RotateCcw,
  Sliders,
  CheckCircle2,
} from "lucide-react";
import { apiFetch } from "../api";

export default function ItemsPage() {
  const navigate = useNavigate();
  const { items, user, stores, consumeItems, refreshItems } = useAppData();
  const { showAlert } = useAppModal();

  const isSiteManager = user?.roles?.includes("SITE_STORE_MANAGER") && !user?.roles?.includes("SYSTEM_ADMINISTRATOR") && !user?.roles?.includes("CENTRAL_STORE_MANAGER");
  
  const [activeTab, setActiveTab] = useState(isSiteManager ? "site" : "catalog");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All Categories");
  const [statusFilter, setStatusFilter] = useState("All Statuses");
  const [categories, setCategories] = useState([]);
  
  // Site Inventory State
  const [siteInventory, setSiteInventory] = useState([]);
  const [loadingSiteInv, setLoadingSiteInv] = useState(false);
  const [selectedStoreId, setSelectedStoreId] = useState("");

  // Modals state
  const [consumeModal, setConsumeModal] = useState(null);
  const [consumeQty, setConsumeQty] = useState("");
  const [consumeNote, setConsumeNote] = useState("");
  const [consumedAt, setConsumedAt] = useState(() => new Date().toISOString().slice(0, 10));

  const [freezeModal, setFreezeModal] = useState(null);
  const [freezeQty, setFreezeQty] = useState("");
  
  const [adjustModal, setAdjustModal] = useState(null);
  const [adjustQty, setAdjustQty] = useState("");
  const [adjustReason, setAdjustReason] = useState("");

  const [busy, setBusy] = useState(false);

  // Available stores
  const activeStores = useMemo(() => stores.filter(s => s.active && !s.closing), [stores]);

  useEffect(() => {
    if (!selectedStoreId && activeStores.length > 0) {
      const siteStore = activeStores.find(s => s.type === "SITE") || activeStores[0];
      setSelectedStoreId(siteStore.id);
    }
  }, [activeStores, selectedStoreId]);

  // Load Categories
  useEffect(() => {
    apiFetch("/api/items/categories")
      .then((res) => {
        const cats = Array.isArray(res) ? res : [];
        setCategories(cats.map(c => typeof c === "string" ? c : c.name || String(c)));
      })
      .catch(() => {
        const unique = [...new Set(items.map(i => i.category).filter(Boolean))];
        setCategories(unique);
      });
  }, [items]);

  // Load Site Inventory
  const fetchSiteInventory = () => {
    setLoadingSiteInv(true);
    const query = selectedStoreId ? `?storeId=${selectedStoreId}` : "";
    apiFetch(`/api/inventory/site-inventory${query}`)
      .then((res) => {
        setSiteInventory(Array.isArray(res) ? res : []);
      })
      .catch((err) => console.error("Failed to load site inventory", err))
      .finally(() => setLoadingSiteInv(false));
  };

  useEffect(() => {
    if (activeTab === "site") {
      fetchSiteInventory();
    }
  }, [activeTab, selectedStoreId]);

  function onAdd() {
    navigate("/items/add-item");
  }

  function onExport() {
    if (activeTab === "catalog") {
      const header = "Name,Code,Category,Available,Reserved,Incoming,Frozen,Status\n";
      const rows = items
        .map((i) =>
          `"${i.name}","${i.code}","${i.category || ""}",${i.available},${i.reserved},${i.incoming},${i.frozen || 0},"${i.status.label}"`
        )
        .join("\n");
      downloadCSV("catalog-export.csv", header + rows);
    } else {
      const header = "Store,Code,Item,Category,OnHand,Available,Reserved,InTransit,Frozen,Consumed\n";
      const rows = siteInventory
        .map((inv) =>
          `"${inv.storeName}","${inv.itemCode}","${inv.itemName}","${inv.category || ""}",${inv.onHand},${inv.available},${inv.reserved},${inv.inTransit},${inv.frozen},${inv.consumed}`
        )
        .join("\n");
      downloadCSV("site-inventory-export.csv", header + rows);
    }
  }

  function downloadCSV(filename, content) {
    const blob = new Blob([content], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  // --- Handlers for Physical Inventory Actions ---
  async function handleConsume(e) {
    e.preventDefault();
    if (!consumeModal || !consumeQty || busy) return;
    const qty = Number(consumeQty);
    if (isNaN(qty) || qty <= 0) {
      showAlert({ title: "Invalid Quantity", message: "Enter a valid positive quantity.", type: "warning" });
      return;
    }
    setBusy(true);
    try {
      await consumeItems(consumeModal.storeId, [{
        itemId: consumeModal.itemId,
        quantity: qty,
        consumedAt,
        notes: consumeNote || null
      }]);
      setConsumeModal(null);
      fetchSiteInventory();
      showAlert({ title: "Consumption Recorded", message: `Successfully logged ${qty} × ${consumeModal.itemName}.`, type: "success" });
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to consume: " + (err?.message || ""), type: "danger" });
    } finally {
      setBusy(false);
    }
  }

  async function handleFreezeToggle(inv, shouldFreeze) {
    setBusy(true);
    try {
      await apiFetch(`/api/inventory/${inv.id}/freeze`, {
        method: "POST",
        body: { freeze: shouldFreeze, quantity: Number(inv.onHand) || 0 }
      });
      fetchSiteInventory();
      showAlert({
        title: shouldFreeze ? "Stock Frozen" : "Stock Unfrozen",
        message: `${inv.itemName} stock has been ${shouldFreeze ? "frozen" : "unfrozen"} for store ${inv.storeName}.`,
        type: shouldFreeze ? "warning" : "success"
      });
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to update freeze status: " + (err?.message || ""), type: "danger" });
    } finally {
      setBusy(false);
    }
  }

  async function handleAdjust(e) {
    e.preventDefault();
    if (!adjustModal || adjustQty === "" || busy) return;
    const newQty = Number(adjustQty);
    if (isNaN(newQty) || newQty < 0) {
      showAlert({ title: "Invalid Quantity", message: "Quantity cannot be negative.", type: "warning" });
      return;
    }
    setBusy(true);
    try {
      await apiFetch(`/api/inventory/${adjustModal.id}/adjust`, {
        method: "POST",
        body: { newQuantity: newQty, reason: adjustReason }
      });
      setAdjustModal(null);
      fetchSiteInventory();
      showAlert({ title: "Stock Adjusted", message: `Physical stock count for ${adjustModal.itemName} updated to ${newQty}.`, type: "success" });
    } catch (err) {
      showAlert({ title: "Error", message: "Failed to adjust stock: " + (err?.message || ""), type: "danger" });
    } finally {
      setBusy(false);
    }
  }

  // Filter Catalog
  const visibleCatalog = useMemo(() => {
    return items.filter((i) => {
      const matchesSearch =
        !search ||
        i.name.toLowerCase().includes(search.toLowerCase()) ||
        i.code.toLowerCase().includes(search.toLowerCase());
      const matchesCategory = category === "All Categories" || i.category === category;
      const matchesStatus =
        statusFilter === "All Statuses" ||
        i.status.label === statusFilter;
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [items, search, category, statusFilter]);

  // Filter Site Inventory
  const visibleSiteInventory = useMemo(() => {
    return siteInventory.filter((inv) => {
      const matchesSearch =
        !search ||
        inv.itemName.toLowerCase().includes(search.toLowerCase()) ||
        inv.itemCode.toLowerCase().includes(search.toLowerCase());
      const matchesCategory = category === "All Categories" || inv.category === category;
      return matchesSearch && matchesCategory;
    });
  }, [siteInventory, search, category]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title={activeTab === "site" ? "Physical Site Inventory" : "Master Item Catalog"}
          badge={activeTab === "site" ? `${visibleSiteInventory.length} inventory records` : `${visibleCatalog.length} items`}
          actions={[
            ...(!isSiteManager ? [{ label: "Add Item", icon: <Plus size={16} />, variant: "primary", onClick: onAdd }] : []),
            { label: "Export", icon: <Download size={16} />, variant: "outline", onClick: onExport },
          ]}
        />

        {/* View Switching Tabs */}
        <div style={{ display: "flex", gap: 12, padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
          <button
            type="button"
            className={`btn ${activeTab === "site" ? "btn-primary" : "btn-outline"}`}
            onClick={() => setActiveTab("site")}
            style={{ fontSize: 13, padding: "6px 14px" }}
          >
            <Package size={15} style={{ marginRight: 6 }} />
            Physical Site Inventory
          </button>
          <button
            type="button"
            className={`btn ${activeTab === "catalog" ? "btn-primary" : "btn-outline"}`}
            onClick={() => setActiveTab("catalog")}
            style={{ fontSize: 13, padding: "6px 14px" }}
          >
            <Sliders size={15} style={{ marginRight: 6 }} />
            Master Catalog
          </button>
        </div>

        {/* Filter Bar */}
        <div className="filters" style={{ padding: "16px", display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          {activeTab === "site" && (
            <div style={{ minWidth: 220 }}>
              <select
                className="input"
                value={selectedStoreId}
                onChange={(e) => setSelectedStoreId(e.target.value)}
              >
                <option value="">All Managed Stores</option>
                {activeStores.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.type})</option>
                ))}
              </select>
            </div>
          )}

          <div style={{ flex: 1, minWidth: 200 }}>
            <input
              className="input"
              placeholder="Search by item name or code…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="select-wrap" style={{ minWidth: 160 }}>
            <select className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
              <option>All Categories</option>
              {categories.map(c => (
                <option key={c}>{c}</option>
              ))}
            </select>
            <ChevronsUpDown size={16} className="select-icon" />
          </div>

          {activeTab === "catalog" && (
            <div className="select-wrap" style={{ minWidth: 140 }}>
              <select
                className="input"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option>All Statuses</option>
                <option>Low Stock</option>
                <option>In Stock</option>
              </select>
              <ChevronsUpDown size={16} className="select-icon" />
            </div>
          )}
        </div>

        {/* TAB 1: Physical Site Inventory Table */}
        {activeTab === "site" && (
          <table className="table">
            <thead>
              <tr>
                <th>Store & Item</th>
                <th>On Hand</th>
                <th>Available</th>
                <th>Reserved</th>
                <th>In Transit</th>
                <th>
                  <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                    <Snowflake size={14} style={{ color: "#7c3aed" }} /> Frozen
                  </span>
                </th>
                <th>Consumed</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {visibleSiteInventory.map((inv) => {
                const isFrozen = Number(inv.frozen) > 0;
                return (
                  <tr key={inv.id}>
                    <td>
                      <div className="item-cell">
                        <span className="item-icon">
                          <Package size={18} />
                        </span>
                        <div>
                          <div className="item-name">{inv.itemName}</div>
                          <div className="item-code">
                            <span style={{ fontWeight: 600, color: "#2563eb" }}>{inv.storeName}</span> • {inv.itemCode}
                            {inv.category ? ` · ${inv.category}` : ""}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td style={{ fontWeight: 700, fontSize: 14 }}>
                      {Number(inv.onHand).toLocaleString()} <span style={{ fontSize: 12, color: "#64748b", fontWeight: 400 }}>{inv.unitOfMeasure || ""}</span>
                    </td>
                    <td>
                      <Badge type={Number(inv.available) > 0 ? "success" : "default"}>
                        {Number(inv.available).toLocaleString()} available
                      </Badge>
                    </td>
                    <td style={{ color: Number(inv.reserved) > 0 ? "#f59e0b" : "#94a3b8" }}>
                      {Number(inv.reserved).toLocaleString()}
                    </td>
                    <td style={{ color: Number(inv.inTransit) > 0 ? "#2563eb" : "#94a3b8" }}>
                      {Number(inv.inTransit).toLocaleString()}
                    </td>
                    <td style={{ color: isFrozen ? "#7c3aed" : "#94a3b8" }}>
                      {isFrozen ? (
                        <span style={{ display: "flex", alignItems: "center", gap: 4, fontWeight: 600 }}>
                          <Snowflake size={13} />
                          {Number(inv.frozen).toLocaleString()}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td style={{ color: "#475569" }}>
                      {Number(inv.consumed).toLocaleString()}
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        <button
                          type="button"
                          className="ch-btn ch-btn--primary"
                          disabled={Number(inv.available) <= 0}
                          onClick={() => {
                            setConsumeModal(inv);
                            setConsumeQty("");
                            setConsumeNote("");
                          }}
                          style={{ fontSize: 12, padding: "4px 8px" }}
                          title="Log item consumption"
                        >
                          <Flame size={13} />
                          Consume
                        </button>

                        <button
                          type="button"
                          className={`ch-btn ${isFrozen ? "ch-btn--success" : "ch-btn--outline"}`}
                          onClick={() => handleFreezeToggle(inv, !isFrozen)}
                          style={{ fontSize: 12, padding: "4px 8px" }}
                          title={isFrozen ? "Unfreeze stock" : "Freeze stock under investigation"}
                        >
                          <Snowflake size={13} />
                          {isFrozen ? "Unfreeze" : "Freeze"}
                        </button>

                        <button
                          type="button"
                          className="ch-btn ch-btn--outline"
                          onClick={() => {
                            setAdjustModal(inv);
                            setAdjustQty(String(inv.onHand));
                            setAdjustReason("");
                          }}
                          style={{ fontSize: 12, padding: "4px 8px" }}
                          title="Adjust physical stock count"
                        >
                          Adjust
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}

              {visibleSiteInventory.length === 0 && (
                <tr>
                  <td colSpan={8} style={{ textAlign: "center", color: "#64748b", padding: "32px 16px" }}>
                    {loadingSiteInv
                      ? "Loading physical inventory…"
                      : "No inventory rows found for this store. Stock will automatically appear here once received on a Material Request or GRN."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}

        {/* TAB 2: Master Item Catalog Table */}
        {activeTab === "catalog" && (
          <table className="table">
            <thead>
              <tr>
                <th>Item</th>
                <th>Available</th>
                <th>Reserved</th>
                <th>Incoming</th>
                <th>
                  <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                    <Snowflake size={14} style={{ color: "#7c3aed" }} /> Frozen
                  </span>
                </th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {visibleCatalog.map((item) => (
                <tr key={item.code}>
                  <td>
                    <div className="item-cell">
                      <span className="item-icon">
                        <Package size={18} />
                      </span>
                      <div>
                        <div className="item-name">{item.name}</div>
                        <div className="item-code">{item.code}{item.category ? ` · ${item.category}` : ""}</div>
                      </div>
                    </div>
                  </td>
                  <td style={{ fontWeight: 600 }}>{Number(item.available).toLocaleString()}</td>
                  <td style={{ color: item.reserved > 0 ? "#f59e0b" : undefined }}>{Number(item.reserved).toLocaleString()}</td>
                  <td style={{ color: "#2563eb" }}>{Number(item.incoming).toLocaleString()}</td>
                  <td style={{ color: item.frozen > 0 ? "#7c3aed" : "#64748b" }}>
                    {item.frozen > 0 ? (
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                        <Snowflake size={13} />
                        {Number(item.frozen).toLocaleString()}
                      </span>
                    ) : (
                      <span style={{ color: "#94a3b8" }}>—</span>
                    )}
                  </td>
                  <td>
                    <Badge type={item.status.type}>{item.status.label}</Badge>
                  </td>
                  <td>
                    {item.status.label === "Low Stock" && !isSiteManager ? (
                      <button
                        type="button"
                        className="ch-btn ch-btn--outline"
                        onClick={() =>
                          navigate("/expected-receipts", { state: { lockedItemId: item.id, lockedItemName: item.name } })
                        }
                        title="Below reorder threshold — create an Expected Receipt to restock"
                      >
                        <ShoppingCart size={16} />
                        Reorder
                      </button>
                    ) : (
                      <span style={{ color: "#64748b", fontSize: 13 }}>—</span>
                    )}
                  </td>
                </tr>
              ))}
              {visibleCatalog.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ textAlign: "center", color: "#64748b" }}>
                    No items match your filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* --- Action Modal: Consume --- */}
      {consumeModal && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh" }}>
          <div className="app-modal" style={{ maxWidth: 420, padding: 24, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Log Consumption</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Log stock utilized at <strong>{consumeModal.storeName}</strong> for <strong>{consumeModal.itemName}</strong> ({consumeModal.itemCode}).
            </p>
            <form onSubmit={handleConsume}>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Available Stock</label>
                <div style={{ fontWeight: 600, fontSize: 15, color: "#16a34a" }}>
                  {consumeModal.available} {consumeModal.unitOfMeasure || "units"}
                </div>
              </div>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Quantity Consumed</label>
                <input
                  type="number"
                  className="input"
                  min="0.01"
                  max={consumeModal.available}
                  step="any"
                  value={consumeQty}
                  onChange={(e) => setConsumeQty(e.target.value)}
                  placeholder="Enter quantity"
                  required
                  autoFocus
                />
              </div>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Consumption Date</label>
                <input
                  type="date"
                  className="input"
                  value={consumedAt}
                  onChange={(e) => setConsumedAt(e.target.value)}
                  required
                />
              </div>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Notes / Purpose (Optional)</label>
                <input
                  className="input"
                  value={consumeNote}
                  onChange={(e) => setConsumeNote(e.target.value)}
                  placeholder="e.g. Used for electrical installation"
                />
              </div>
              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setConsumeModal(null)} disabled={busy}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  <Flame size={15} />
                  {busy ? "Saving…" : "Confirm Consumption"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- Action Modal: Adjust Stock --- */}
      {adjustModal && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh" }}>
          <div className="app-modal" style={{ maxWidth: 420, padding: 24, textAlign: "left" }}>
            <h3 style={{ marginTop: 0, marginBottom: 4 }}>Adjust Physical Count</h3>
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Directly adjust on-hand count for <strong>{adjustModal.itemName}</strong> at <strong>{adjustModal.storeName}</strong>.
            </p>
            <form onSubmit={handleAdjust}>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Current On-Hand Count</label>
                <div style={{ fontWeight: 600, fontSize: 15 }}>
                  {adjustModal.onHand} {adjustModal.unitOfMeasure || "units"}
                </div>
              </div>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>New On-Hand Count</label>
                <input
                  type="number"
                  className="input"
                  min="0"
                  step="any"
                  value={adjustQty}
                  onChange={(e) => setAdjustQty(e.target.value)}
                  placeholder="Enter new physical count"
                  required
                  autoFocus
                />
              </div>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: "block", fontWeight: 500, marginBottom: 4, fontSize: 13 }}>Reason for Adjustment</label>
                <input
                  className="input"
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                  placeholder="e.g. Physical audit count correction"
                  required
                />
              </div>
              <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                <button type="button" className="btn btn-outline" onClick={() => setAdjustModal(null)} disabled={busy}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={busy}>
                  <CheckCircle2 size={15} />
                  {busy ? "Saving…" : "Save Adjustment"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
