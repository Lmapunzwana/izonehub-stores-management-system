import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { X, Save, ChevronsUpDown } from "lucide-react";
import { useAppData } from "../context/AppDataContext";

export default function AddItemPage() {
  const navigate = useNavigate();
  const { addItem } = useAppData();
  const [form, setForm] = useState({
    name: "",
    code: "",
    category: "Electrical",
    unit: "Piece",
    minStock: "",
    reorderPoint: "",
  });
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState(null);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function onCancel() {
    navigate("/items");
  }

  async function onSave() {
    setError(null);
    if (!form.name.trim() || !form.code.trim()) {
      setSaved(false);
      setError("Item Name and Item Code are required.");
      return;
    }
    
    try {
      await addItem({
        name: form.name,
        code: form.code,
        category: form.category.toUpperCase(),
        unitOfMeasure: form.unit,
        reorderThreshold: Number(form.reorderPoint) || 0,
      });
      setSaved(true);
      setTimeout(() => navigate("/items"), 600);
    } catch (err) {
      setSaved(false);
      setError(err.message || "Failed to save item. The code might already exist.");
    }
  }

  return (
    <div className="page">
      <div className="card">
        <h2 className="card-title">Item Information</h2>
        <div className="form-grid">
          <div>
            <label>Item Name</label>
            <input
              className="input"
              placeholder="Solar Panel 550W"
              value={form.name}
              onChange={(e) => update("name", e.target.value)}
            />
          </div>
          <div>
            <label>Item Code</label>
            <input
              className="input"
              placeholder="SP-550W"
              value={form.code}
              onChange={(e) => update("code", e.target.value)}
            />
          </div>
          <div>
            <label>Category</label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.category}
                onChange={(e) => update("category", e.target.value)}
              >
                <option>Electrical</option>
                <option>Safety</option>
              </select>
              <ChevronsUpDown size={16} className="select-icon" />
            </div>
          </div>
          <div>
            <label>Unit of Measure</label>
            <div className="select-wrap">
              <select
                className="input"
                value={form.unit}
                onChange={(e) => update("unit", e.target.value)}
              >
                <option>Piece</option>
                <option>Box</option>
              </select>
              <ChevronsUpDown size={16} className="select-icon" />
            </div>
          </div>
        </div>

        <hr className="divider" />

        <h2 className="card-title">Stock Controls</h2>
        <div className="form-grid">
          <div>
            <label>Minimum Stock Level</label>
            <input
              className="input"
              type="number"
              placeholder="100"
              value={form.minStock}
              onChange={(e) => update("minStock", e.target.value)}
            />
          </div>
          <div>
            <label>Reorder Point</label>
            <input
              className="input"
              type="number"
              placeholder="150"
              value={form.reorderPoint}
              onChange={(e) => update("reorderPoint", e.target.value)}
            />
          </div>
        </div>

        <hr className="divider" />

        {error && (
          <div style={{ color: "var(--danger-color, #dc2626)", marginBottom: "16px", fontWeight: "500" }}>
            {error}
          </div>
        )}

        <div className="actions-row">
          {saved && (
            <span style={{ color: "#16a34a", fontWeight: 600, marginRight: "auto" }}>
              Item saved
            </span>
          )}
          <button type="button" className="ch-btn ch-btn--outline" onClick={onCancel}>
            <X size={16} />
            Cancel
          </button>
          <button type="button" className="ch-btn ch-btn--primary" onClick={onSave}>
            <Save size={16} />
            Save Item
          </button>
        </div>
      </div>
    </div>
  );
}
