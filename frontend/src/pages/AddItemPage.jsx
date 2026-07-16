import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { X, Save, ChevronsUpDown, Package } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { apiFetch } from "../api";

const UNITS_OF_MEASURE = [
  "Piece", "Box", "Bag", "Metre", "Kilogram", "Litre",
  "Roll", "Set", "Pair", "Pack", "Drum", "Bundle",
  "Tonne", "Each",
];

export default function AddItemPage() {
  const navigate = useNavigate();
  const { addItem } = useAppData();
  const [form, setForm] = useState({
    name: "",
    code: "",
    description: "",
    category: "",
    unit: "Piece",
    reorderPoint: "0",
  });
  const [categories, setCategories] = useState([]);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState(null);

  // Load real categories from the backend
  useEffect(() => {
    apiFetch("/api/items/categories")
      .then((res) => {
        const cats = Array.isArray(res) ? res : [];
        setCategories(cats.map((c) => (typeof c === "string" ? c : String(c))));
        if (cats.length > 0 && !form.category) {
          setForm((f) => ({ ...f, category: cats[0] }));
        }
      })
      .catch(() => {
        // Fallback to known categories if API fails
        const fallback = ["CONSTRUCTION", "ELECTRICAL", "SOLAR", "CONSUMABLE", "TOOL", "SPARE_PART", "SAFETY"];
        setCategories(fallback);
        setForm((f) => ({ ...f, category: fallback[0] }));
      });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function onCancel() {
    navigate("/items");
  }

  async function onSave() {
    setError(null);
    setSaved(false);

    // Validation
    if (!form.name.trim()) { setError("Item Name is required."); return; }
    if (!form.code.trim()) { setError("Item Code is required."); return; }
    if (!form.category)    { setError("Category is required."); return; }
    if (!form.unit.trim()) { setError("Unit of Measure is required."); return; }

    const threshold = parseFloat(form.reorderPoint);
    if (isNaN(threshold) || threshold < 0) {
      setError("Reorder Point must be a non-negative number.");
      return;
    }

    setSaving(true);
    try {
      await addItem({
        name:             form.name.trim(),
        code:             form.code.trim().toUpperCase(),
        description:      form.description.trim() || null,
        unitOfMeasure:    form.unit,
        category:         form.category.toUpperCase(),
        reorderThreshold: threshold,
      });
      setSaved(true);
      setTimeout(() => navigate("/items"), 800);
    } catch (err) {
      setSaved(false);
      // Surface the server error message if it has one
      const msg =
        err?.message ||
        err?.error ||
        (typeof err === "string" ? err : null) ||
        "Failed to save item. The code might already exist.";
      setError(msg);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 24 }}>
          <span style={{
            width: 36, height: 36, borderRadius: 8,
            background: "var(--brand-gradient, linear-gradient(135deg,#1d4ed8,#7c3aed))",
            display: "flex", alignItems: "center", justifyContent: "center",
          }}>
            <Package size={18} color="#fff" />
          </span>
          <h2 className="card-title" style={{ margin: 0 }}>Add New Item</h2>
        </div>

        <h3 className="card-title" style={{ fontSize: 14, color: "#64748b", marginBottom: 12 }}>
          Item Information
        </h3>
        <div className="form-grid">
          <div>
            <label>Item Name <span style={{ color: "#dc2626" }}>*</span></label>
            <input
              className="input"
              placeholder="e.g. Cement 50kg Bag"
              value={form.name}
              onChange={(e) => update("name", e.target.value)}
            />
          </div>
          <div>
            <label>Item Code <span style={{ color: "#dc2626" }}>*</span></label>
            <input
              className="input"
              placeholder="e.g. CEM-50KG"
              value={form.code}
              onChange={(e) => update("code", e.target.value)}
            />
            <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
              Unique identifier — will be converted to uppercase
            </div>
          </div>
          <div style={{ gridColumn: "1 / -1" }}>
            <label>Description</label>
            <input
              className="input"
              placeholder="Optional — brief description of the item"
              value={form.description}
              onChange={(e) => update("description", e.target.value)}
            />
          </div>
          <div>
            <label>Category <span style={{ color: "#dc2626" }}>*</span></label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.category}
                onChange={(e) => update("category", e.target.value)}
              >
                {categories.length === 0 && (
                  <option value="">Loading…</option>
                )}
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              <ChevronsUpDown size={16} className="select-icon" />
            </div>
          </div>
          <div>
            <label>Unit of Measure <span style={{ color: "#dc2626" }}>*</span></label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.unit}
                onChange={(e) => update("unit", e.target.value)}
              >
                {UNITS_OF_MEASURE.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
              <ChevronsUpDown size={16} className="select-icon" />
            </div>
          </div>
        </div>

        <hr className="divider" />

        <h3 className="card-title" style={{ fontSize: 14, color: "#64748b", marginBottom: 12 }}>
          Stock Controls
        </h3>
        <div className="form-grid">
          <div>
            <label>Reorder Point</label>
            <input
              className="input"
              type="number"
              min="0"
              step="any"
              placeholder="0"
              value={form.reorderPoint}
              onChange={(e) => update("reorderPoint", e.target.value)}
            />
            <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 4 }}>
              Low Stock alert triggers when available quantity falls at or below this value
            </div>
          </div>
        </div>

        <hr className="divider" />

        {error && (
          <div style={{
            color: "#dc2626", background: "#fef2f2", border: "1px solid #fecaca",
            borderRadius: 6, padding: "10px 14px", marginBottom: 16, fontSize: 13, fontWeight: 500,
          }}>
            {error}
          </div>
        )}

        <div className="actions-row">
          {saved && (
            <span style={{ color: "#16a34a", fontWeight: 600, marginRight: "auto" }}>
              ✓ Item saved successfully!
            </span>
          )}
          <button type="button" className="ch-btn ch-btn--outline" onClick={onCancel} disabled={saving}>
            <X size={16} />
            Cancel
          </button>
          <button type="button" className="ch-btn ch-btn--primary" onClick={onSave} disabled={saving || saved}>
            <Save size={16} />
            {saving ? "Saving…" : "Save Item"}
          </button>
        </div>
      </div>
    </div>
  );
}
