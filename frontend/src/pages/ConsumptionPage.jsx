import { useMemo, useState } from "react";
import Badge from "../components/Badge";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { Package, Flame, Search } from "lucide-react";

export default function ConsumptionPage() {
  const { items, defaultStoreId, consumeItems } = useAppData();
  const { showAlert } = useAppModal();
  const [search, setSearch] = useState("");
  
  const [consumeModalOpen, setConsumeModalOpen] = useState(false);
  const [consumeItem, setConsumeItem] = useState(null);
  const [consumeQty, setConsumeQty] = useState("");

  async function handleConsume(e) {
    e.preventDefault();
    if (!consumeItem || !consumeQty) return;
    try {
      await consumeItems(defaultStoreId, [{ itemId: consumeItem.id, quantity: Number(consumeQty) }]);
      setConsumeModalOpen(false);
      setConsumeItem(null);
      setConsumeQty("");
      showAlert({ title: "Success", message: "Item successfully consumed from inventory.", type: "success" });
    } catch (err) {
      console.error(err);
      showAlert({ title: "Error", message: "Failed to consume item. " + err.message, type: "danger" });
    }
  }

  const visibleItems = useMemo(() => {
    // Only show items that have actual physical stock > 0
    return items.filter((i) => {
      const hasStock = i.available > 0;
      const matchesSearch =
        !search ||
        i.name.toLowerCase().includes(search.toLowerCase()) ||
        i.code.toLowerCase().includes(search.toLowerCase());
      return hasStock && matchesSearch;
    });
  }, [items, search]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Item Consumption"
          badge={`${visibleItems.length} items available`}
          icon={<Flame size={20} />}
          subtitle="Log items that have been utilized at this site"
        />

        <div className="filters" style={{ padding: "16px", borderBottom: "1px solid #f1f5f9" }}>
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
                <td>{item.category}</td>
                <td>
                  <Badge type="success">{item.available} in stock</Badge>
                </td>
                <td>
                  <button
                    type="button"
                    className="ch-btn ch-btn--primary"
                    onClick={() => {
                      setConsumeItem(item);
                      setConsumeModalOpen(true);
                    }}
                    title="Log item consumption"
                  >
                    Consume
                  </button>
                </td>
              </tr>
            ))}
            {visibleItems.length === 0 && (
              <tr>
                <td colSpan={4} style={{ textAlign: "center", color: "#64748b", padding: "32px 16px" }}>
                  {items.some(i => i.available > 0) 
                    ? "No items match your search." 
                    : "You currently have no physical stock available to consume. Receive requested items first."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {consumeModalOpen && consumeItem && (
        <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh", overflowY: "auto" }}>
          <div className="app-modal" style={{ maxWidth: 400, padding: 24, textAlign: "left" }}>
            <h3 style={{ marginTop: 0 }}>Consume {consumeItem.name}</h3>
            <p style={{ color: "#64748b", fontSize: 14 }}>
              Available in physical inventory: <strong>{consumeItem.available}</strong>
            </p>
            <form onSubmit={handleConsume}>
              <div className="form-group">
                <label>Quantity Consumed</label>
                <input
                  type="number"
                  className="input"
                  min="1"
                  max={consumeItem.available}
                  step="any"
                  required
                  value={consumeQty}
                  onChange={(e) => setConsumeQty(e.target.value)}
                  autoFocus
                />
              </div>
              <div className="modal-actions" style={{ marginTop: 24, display: "flex", gap: 8, justifyContent: "flex-end" }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => {
                    setConsumeModalOpen(false);
                    setConsumeItem(null);
                    setConsumeQty("");
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Confirm Consumption
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
