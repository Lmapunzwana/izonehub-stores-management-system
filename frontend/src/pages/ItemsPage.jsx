import { useMemo, useState } from "react";
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
} from "lucide-react";

export default function ItemsPage() {
  const navigate = useNavigate();
  const { items, user, defaultStoreId, consumeItems } = useAppData();
  const { showAlert } = useAppModal();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All Categories");
  const [statusFilter, setStatusFilter] = useState("All Statuses");
  const [appliedFilters, setAppliedFilters] = useState({
    search: "",
    category: "All Categories",
    statusFilter: "All Statuses",
  });
  const [consumeModalOpen, setConsumeModalOpen] = useState(false);
  const [consumeItem, setConsumeItem] = useState(null);
  const [consumeQty, setConsumeQty] = useState("");

  function onAdd() {
    navigate("/items/add-item");
  }

  function onExport() {
    const header = "Name,Code,Available,Reserved,Incoming,Status\n";
    const rows = items
      .map((i) => `${i.name},${i.code},${i.available},${i.reserved},${i.incoming},${i.status.label}`)
      .join("\n");
    const blob = new Blob([header + rows], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "items-export.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  function onFilter() {
    setAppliedFilters({ search, category, statusFilter });
  }

  async function handleConsume(e) {
    e.preventDefault();
    if (!consumeItem || !consumeQty) return;
    try {
      await consumeItems(defaultStoreId, [{ itemId: consumeItem.id, quantity: Number(consumeQty) }]);
      setConsumeModalOpen(false);
      setConsumeItem(null);
      setConsumeQty("");
    } catch (err) {
      console.error(err);
      showAlert({ title: "Error", message: "Failed to consume item. " + err.message, type: "danger" });
    }
  }

  const visibleItems = useMemo(() => {
    return items.filter((i) => {
      const matchesSearch =
        !appliedFilters.search ||
        i.name.toLowerCase().includes(appliedFilters.search.toLowerCase()) ||
        i.code.toLowerCase().includes(appliedFilters.search.toLowerCase());
      const matchesCategory =
        appliedFilters.category === "All Categories" || i.category === appliedFilters.category;
      const matchesStatus =
        appliedFilters.statusFilter === "All Statuses" ||
        i.status.label === appliedFilters.statusFilter;
      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [items, appliedFilters]);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Items"
          badge={`${items.length} items`}
          actions={[
            { label: "Add Item", icon: <Plus size={16} />, variant: "primary", onClick: onAdd },
            { label: "Export", icon: <Download size={16} />, variant: "outline", onClick: onExport },
          ]}
        />

        <div className="filters">
          <input
            className="input"
            placeholder="Search by item name or code"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && onFilter()}
          />
          <div className="select-wrap">
            <select className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
              <option>All Categories</option>
              <option>Electrical</option>
              <option>Safety</option>
            </select>
            <ChevronsUpDown size={16} className="select-icon" />
          </div>
          <div className="select-wrap">
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
          <button type="button" className="ch-btn ch-btn--outline" onClick={onFilter}>
            <Filter size={16} />
            Filter
          </button>
        </div>

        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Available</th>
              <th>Reserved</th>
              <th>Incoming</th>
              <th>Status</th>
              <th></th>
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
                <td>{item.available}</td>
                <td>{item.reserved}</td>
                <td>{item.incoming}</td>
                <td>
                  <Badge type={item.status.type}>{item.status.label}</Badge>
                </td>
                <td>
                  {item.status.label === "Low Stock" ? (
                    <button
                      type="button"
                      className="ch-btn ch-btn--outline"
                      onClick={() => navigate("/expected-receipts")}
                      title="Below reorder threshold — create an Expected Receipt to restock"
                    >
                      <ShoppingCart size={16} />
                      Reorder
                    </button>
                  ) : user?.roles?.includes("SITE_STORE_MANAGER") && item.available > 0 ? (
                    <button
                      type="button"
                      className="ch-btn ch-btn--primary"
                      onClick={() => {
                        setConsumeItem(item);
                        setConsumeModalOpen(true);
                      }}
                      title="Consume items from physical inventory"
                    >
                      Consume
                    </button>
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>—</span>
                  )}
                </td>
              </tr>
            ))}
            {visibleItems.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No items match your filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {consumeModalOpen && consumeItem && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: 400 }}>
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
                />
              </div>
              <div className="modal-actions" style={{ marginTop: 24, display: "flex", gap: 8, justifyContent: "flex-end" }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => {
                    setConsumeModalOpen(false);
                    setConsumeItem(null);
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
