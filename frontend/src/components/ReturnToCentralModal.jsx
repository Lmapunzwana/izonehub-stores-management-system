import { useState, useEffect } from "react";
import { X, Plus, Trash2 } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";
import { apiFetch } from "../api";

export default function ReturnToCentralModal({ isOpen, onClose, onSuccess }) {
  const { stores, items, projects, user } = useAppData();
  const { showAlert } = useAppModal();

  const siteStores = stores.filter(s => s.type === "SITE" && s.active);
  const centralStores = stores.filter(s => s.type === "CENTRAL" && s.active);
  const activeProjects = projects.filter(p => p.status === "IN_PROGRESS");

  const [sourceStoreId, setSourceStoreId] = useState("");
  const [requestingStoreId, setRequestingStoreId] = useState("");
  const [projectId, setProjectId] = useState("");
  const [lines, setLines] = useState([]);
  const [selectedItem, setSelectedItem] = useState("");
  const [quantity, setQuantity] = useState("");

  useEffect(() => {
    if (isOpen) {
      setSourceStoreId("");
      setRequestingStoreId("");
      setProjectId("");
      setLines([]);
      setSelectedItem("");
      setQuantity("");
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleAddLine = () => {
    if (!selectedItem || !quantity || Number(quantity) <= 0) return;
    const itemObj = items.find(i => i.id === selectedItem);
    if (!itemObj) return;

    setLines(prev => {
      const existing = prev.find(l => l.itemId === selectedItem);
      if (existing) {
        return prev.map(l => l.itemId === selectedItem ? { ...l, quantity: l.quantity + Number(quantity) } : l);
      }
      return [...prev, { itemId: selectedItem, itemName: itemObj.name, quantity: Number(quantity) }];
    });
    setSelectedItem("");
    setQuantity("");
  };

  const removeLine = (index) => {
    setLines(prev => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = async () => {
    if (!sourceStoreId || !requestingStoreId || !projectId) {
      showAlert({ title: "Validation Error", message: "Please select a Site Store, Central Store, and Project.", type: "warning" });
      return;
    }
    if (lines.length === 0) {
      showAlert({ title: "Validation Error", message: "Please add at least one item to return.", type: "warning" });
      return;
    }

    try {
      await apiFetch("/api/material-requests/standalone-return", {
        method: "POST",
        body: {
          sourceStoreId,
          requestingStoreId,
          projectId,
          lines: lines.map(l => ({ itemId: l.itemId, requestedQuantity: l.quantity }))
        }
      });
      showAlert({ title: "Success", message: "Return transfer to Central initiated successfully.", type: "success" });
      onSuccess();
      onClose();
    } catch (e) {
      console.error(e);
      showAlert({ title: "Error", message: "Failed to initiate return. " + e.message, type: "danger" });
    }
  };

  return (
    <div className="app-modal-backdrop" style={{ alignItems: "flex-start", paddingTop: "5vh", overflowY: "auto" }}>
      <div className="app-modal" style={{ maxWidth: 600, padding: 24, textAlign: "left" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h2 style={{ margin: 0, fontSize: "1.25rem" }}>Return Stock to Central</h2>
          <button type="button" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#64748b" }}>
            <X size={20} />
          </button>
        </div>
        
        <div className="form-grid" style={{ marginBottom: 20 }}>
          <div>
            <label>From (Site Store)</label>
            <select className="input" value={sourceStoreId} onChange={e => setSourceStoreId(e.target.value)}>
              <option value="">Select source...</option>
              {siteStores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div>
            <label>To (Central Store)</label>
            <select className="input" value={requestingStoreId} onChange={e => setRequestingStoreId(e.target.value)}>
              <option value="">Select destination...</option>
              {centralStores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div style={{ gridColumn: "1 / -1" }}>
            <label>Related Project</label>
            <select className="input" value={projectId} onChange={e => setProjectId(e.target.value)}>
              <option value="">Select project...</option>
              {activeProjects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </div>
        </div>

        <div style={{ background: "#f8fafc", padding: 16, borderRadius: 8, marginBottom: 20 }}>
          <h3 style={{ fontSize: "0.9rem", margin: "0 0 12px 0" }}>Add Items to Return</h3>
          <div style={{ display: "flex", gap: 8 }}>
            <select className="input" style={{ flex: 2 }} value={selectedItem} onChange={e => setSelectedItem(e.target.value)}>
              <option value="">Select Item...</option>
              {items.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
            <input 
              type="number" 
              className="input" 
              style={{ flex: 1 }} 
              placeholder="Qty" 
              value={quantity} 
              onChange={e => setQuantity(e.target.value)} 
            />
            <button className="btn btn-outline" onClick={handleAddLine}><Plus size={16} /></button>
          </div>
        </div>

        {lines.length > 0 && (
          <table className="table" style={{ marginBottom: 20 }}>
            <thead>
              <tr>
                <th>Item</th>
                <th>Quantity</th>
                <th style={{ width: 40 }}></th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l, i) => (
                <tr key={i}>
                  <td>{l.itemName}</td>
                  <td>{l.quantity}</td>
                  <td>
                    <button className="ch-btn ch-btn--danger" style={{ padding: 4 }} onClick={() => removeLine(i)}>
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 12, marginTop: 24 }}>
          <button className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={handleSubmit}>Submit Return</button>
        </div>
      </div>
    </div>
  );
}
