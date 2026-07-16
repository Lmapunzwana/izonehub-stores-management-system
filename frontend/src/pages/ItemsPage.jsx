import { useMemo, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "../components/Badge";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";
import {
  Plus,
  Download,
  ChevronsUpDown,
  Filter,
  Package,
  ShoppingCart,
  Snowflake,
} from "lucide-react";
import { apiFetch } from "../api";

export default function ItemsPage() {
  const navigate = useNavigate();
  const { items, user } = useAppData();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All Categories");
  const [statusFilter, setStatusFilter] = useState("All Statuses");
  const [appliedFilters, setAppliedFilters] = useState({
    search: "",
    category: "All Categories",
    statusFilter: "All Statuses",
  });
  const [categories, setCategories] = useState([]);

  // Load real categories from API
  useEffect(() => {
    apiFetch("/api/items/categories")
      .then((res) => {
        const cats = Array.isArray(res) ? res : [];
        setCategories(cats.map(c => typeof c === "string" ? c : c.name || String(c)));
      })
      .catch(() => {
        // Derive categories from items as fallback
        const unique = [...new Set(items.map(i => i.category).filter(Boolean))];
        setCategories(unique);
      });
  }, [items]);

  function onAdd() {
    navigate("/items/add-item");
  }

  function onExport() {
    const header = "Name,Code,Category,Available,Reserved,Incoming,Frozen,Status\n";
    const rows = items
      .map((i) =>
        `"${i.name}","${i.code}","${i.category || ""}",${i.available},${i.reserved},${i.incoming},${i.frozen || 0},"${i.status.label}"`
      )
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
              {categories.map(c => (
                <option key={c}>{c}</option>
              ))}
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
            {visibleItems.map((item) => (
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
                  {item.status.label === "Low Stock" ? (
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
            {visibleItems.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: "center", color: "#64748b" }}>
                  No items match your filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
