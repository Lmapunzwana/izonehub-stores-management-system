import { useMemo, useState } from "react";
import { Truck, Download, Plus, Mail, Phone, X, Save, Star } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

const EMPTY_FORM = {
  name: "",
  category: "",
  email: "",
  phone: "",
  address: "",
  leadTime: "",
  status: "Active",
};

export default function SuppliersPage() {
  const { suppliers, addSupplier, updateSupplier } = useAppData();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All Categories");
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);

  const categories = useMemo(
    () => ["All Categories", ...new Set(suppliers.map((s) => s.category))],
    [suppliers]
  );

  const visible = suppliers.filter((s) => {
    const matchesSearch =
      !search ||
      s.name.toLowerCase().includes(search.toLowerCase()) ||
      s.category.toLowerCase().includes(search.toLowerCase());
    const matchesCategory = category === "All Categories" || s.category === category;
    return matchesSearch && matchesCategory;
  });

  function onExport() {
    const header = "Name,Category,Email,Phone,Lead Time,Accuracy,Rating,Status\n";
    const rows = suppliers
      .map(
        (s) =>
          `${s.name},${s.category},${s.email},${s.phone},${s.leadTime},${s.accuracy ?? ""},${s.rating ?? ""},${s.status}`
      )
      .join("\n");
    const blob = new Blob([header + rows], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "suppliers-export.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  function onEdit(id) {
    const s = suppliers.find((s) => s.id === id);
    setEditingId(id);
    setForm({ ...s });
  }

  function onCancel() {
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  function onSave() {
    if (!form.name.trim()) return;
    if (editingId) {
      updateSupplier(editingId, form);
    } else {
      addSupplier(form);
    }
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<Truck size={20} />}
          title="Suppliers"
          subtitle="Manage supplier records and performance details"
          status={{ label: `${suppliers.length} active`, variant: "success" }}
          right={
            <div style={{ display: "flex", gap: 10 }}>
              <button className="ch-btn ch-btn--outline" onClick={onExport}>
                <Download size={16} />
                Export
              </button>
              <button
                className="ch-btn ch-btn--primary"
                onClick={() => {
                  setEditingName(null);
                  setForm(EMPTY_FORM);
                }}
              >
                <Plus size={16} />
                Add Supplier
              </button>
            </div>
          }
        />

        <div className="filters">
          <input
            className="input"
            placeholder="Search suppliers..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="select-wrap">
            <select className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
              {categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
          <span style={{ marginLeft: "auto", fontSize: 13, color: "#64748b", alignSelf: "center" }}>
            Showing {visible.length} suppliers
          </span>
        </div>

        <table className="table">
          <thead>
            <tr>
              <th>Supplier</th>
              <th>Contact</th>
              <th>Lead Time</th>
              <th>Accuracy</th>
              <th>Rating</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {visible.map((s) => (
              <tr key={s.name}>
                <td>
                  <div className="item-cell">
                    <span className="item-icon">
                      <Truck size={18} />
                    </span>
                    <div>
                      <div className="item-name">{s.name}</div>
                      <div className="item-code">{s.category}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <div style={{ fontSize: 13, display: "flex", flexDirection: "column", gap: 2 }}>
                    <span>
                      <Mail size={12} style={{ verticalAlign: "-1px", marginRight: 4 }} />
                      {s.email}
                    </span>
                    <span>
                      <Phone size={12} style={{ verticalAlign: "-1px", marginRight: 4 }} />
                      {s.phone}
                    </span>
                  </div>
                </td>
                <td>{s.leadTime} days</td>
                <td>{s.accuracy != null ? `${s.accuracy}%` : "—"}</td>
                <td>{s.rating != null ? <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Star size={12} fill="#f59e0b" color="#f59e0b" /> {s.rating}</span> : "—"}</td>
                <td>
                  <div className="action-buttons">
                    <button className="btn">View</button>
                    <button className="btn" onClick={() => onEdit(s.id)}>
                      Edit
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "#64748b" }}>
                  No suppliers match your search.
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <hr className="divider" />

        <h2 className="card-title">{editingId ? "Edit Supplier" : "Add Supplier"}</h2>
        <div className="form-grid">
          <div>
            <label>Supplier Name</label>
            <input
              className="input"
              placeholder="ABC Solar"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div>
            <label>Category</label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.category}
                onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
              >
                <option value="">Select category</option>
                <option>Electrical Equipment</option>
                <option>Batteries & Cables</option>
                <option>Tools & Hardware</option>
              </select>
            </div>
          </div>
          <div>
            <label>Email</label>
            <input
              className="input"
              placeholder="sales@abcsolar.com"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            />
          </div>
          <div>
            <label>Phone</label>
            <input
              className="input"
              placeholder="+263..."
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
            />
          </div>
          <div className="full">
            <label>Address</label>
            <input
              className="input"
              placeholder="12 Industrial Road, Harare"
              value={form.address}
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
            />
          </div>
          <div>
            <label>Expected Lead Time (days)</label>
            <input
              className="input"
              type="number"
              placeholder="5"
              value={form.leadTime}
              onChange={(e) => setForm((f) => ({ ...f, leadTime: e.target.value }))}
            />
          </div>
          <div>
            <label>Status</label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.status}
                onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
              >
                <option>Active</option>
                <option>Inactive</option>
              </select>
            </div>
          </div>
        </div>

        <hr className="divider" />

        <div className="actions-row">
          <button className="ch-btn ch-btn--outline" onClick={onCancel}>
            <X size={16} />
            Cancel
          </button>
          <button className="ch-btn ch-btn--primary" onClick={onSave}>
            <Save size={16} />
            Save Supplier
          </button>
        </div>
      </div>
    </div>
  );
}
