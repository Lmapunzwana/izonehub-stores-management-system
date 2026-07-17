import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  CirclePlus,
  Package,
  CheckCircle2,
  Lock,
  Truck,
  Info,
  Trash2,
  X,
  Plus,
} from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";
import { apiFetch } from "../api";

export default function AddItemToRequestPage() {
  const navigate = useNavigate();
  const { items, addMaterialRequest, projects, stores } = useAppData();
  const [itemsInRequest, setItemsInRequest] = useState([]);
  const [busy, setBusy] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState(projects[0]?.id || "");
  const [allStores, setAllStores] = useState([]);
  
  const [sourceStoreId, setSourceStoreId] = useState("");

  useEffect(() => {
    apiFetch("/api/stores?size=200&active=true&managedOnly=false")
      .then((res) => {
        const arr = Array.isArray(res) ? res : res.content || [];
        const actives = arr.filter(s => s.active && !s.closing);
        setAllStores(actives);
        const central = actives.find(s => s.type === "CENTRAL");
        if (central && !sourceStoreId) {
          setSourceStoreId(central.id);
        }
      })
      .catch((e) => console.error(e));
  }, []);

  const [selectedItem, setSelectedItem] = useState(items[0]?.name || "");
  const [quantity, setQuantity] = useState(30);

  const [sourceStoreStock, setSourceStoreStock] = useState({});
  useEffect(() => {
    if (sourceStoreId) {
      apiFetch(`/api/reports/current-stock?storeId=${sourceStoreId}`)
        .then((res) => {
          const arr = Array.isArray(res) ? res : res.content || [];
          const stockMap = {};
          arr.forEach(r => {
             stockMap[r.itemCode] = {
                 id: r.itemId,
                 name: r.itemName,
                 available: Math.max(0, Number(r.onHand || 0) - Number(r.reserved || 0)),
                 reserved: Number(r.reserved || 0),
                 incoming: Number(r.inTransit || 0)
             };
          });
          setSourceStoreStock(stockMap);
          
          // Auto-select the first available item if we haven't selected one
          const keys = Object.keys(stockMap);
          if (keys.length > 0) {
              setSelectedItem(stockMap[keys[0]].name);
          }
        })
        .catch((e) => console.error("Failed to fetch source store stock", e));
    }
  }, [sourceStoreId]);

  const selectedProject = projects.find((p) => p.id === selectedProjectId);
  
  // Find the selected item's code by searching our source stock map
  const selectedItemCode = Object.keys(sourceStoreStock).find(
    code => sourceStoreStock[code].name === selectedItem
  );
  
  const sourceStats = (sourceStoreId && selectedItemCode && sourceStoreStock[selectedItemCode]) 
        || { available: 0, reserved: 0, incoming: 0, id: null };
        
  const available = sourceStats.available;
    
  const requestedNum = Number(quantity) || 0;
  const isValid = requestedNum > 0 && requestedNum <= available && !!selectedProjectId && !!sourceStoreId;

  function onCancel() {
    navigate("/material-requests");
  }

  function onAddItem() {
    if (!isValid || !selectedProject) return;
    
    if (itemsInRequest.some(i => i.itemId === sourceStats.id)) {
      alert("This item is already in the request. Please remove it first to adjust the quantity.");
      return;
    }

    setItemsInRequest((rows) => [
      ...rows,
      { 
        itemId: sourceStats.id, 
        item: selectedItem, 
        quantity: Number(quantity), 
        status: "Draft" 
      },
    ]);
    setQuantity(0);
  }

  async function onSubmitRequest() {
    if (!selectedProject || busy || itemsInRequest.length === 0) return;
    setBusy(true);
    const requestingStoreId = selectedProject.original.siteStore?.id;
    if (!requestingStoreId) {
      setBusy(false);
      return;
    }

    try {
      await addMaterialRequest({
        projectId: selectedProject.id,
        requestingStoreId,
        sourceStoreId,
        notes: "Requested via UI",
        lines: itemsInRequest.map(r => ({ itemId: r.itemId, requestedQuantity: r.quantity }))
      });
      navigate("/material-requests");
    } finally {
      setBusy(false);
    }
  }

  function onRemove(index) {
    setItemsInRequest((rows) => rows.filter((_, i) => i !== index));
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<CirclePlus size={20} />}
          title="Create Material Request"
          subtitle={selectedProject ? [`Project: ${selectedProject.name}`, `Site: ${selectedProject.original.siteStore?.name || "—"}`] : []}
          status={{ label: isValid ? "Stock validated" : "Select a project", variant: isValid ? "success" : "warning" }}
        />

        <div className="form-grid">
          <div>
            <label>Project</label>
            <select
              className="input"
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
            >
              <option value="">Select a project…</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.code})
                </option>
              ))}
            </select>
            {projects.length === 0 && (
              <p style={{ fontSize: 12, color: "#dc2626", marginTop: 4 }}>
                No active projects — create one under Projects first.
              </p>
            )}
          </div>
          <div>
            <label>Source Store</label>
            <select
              className="input"
              value={sourceStoreId}
              onChange={(e) => setSourceStoreId(e.target.value)}
            >
              <option value="">Select a source store…</option>
              {allStores.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label>Item</label>
            <div className="select-wrap">
              <select
                className="input"
                value={selectedItem}
                onChange={(e) => setSelectedItem(e.target.value)}
              >
                {Object.values(sourceStoreStock).map((i) => (
                  <option key={i.name}>{i.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label>Quantity Requested</label>
            <input
              className="input"
              type="number"
              placeholder="Enter quantity"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </div>
        </div>

        <div className="sub-panel">
          <div className="sub-panel-header">
            <div className="sub-panel-header-left">
              <Package size={18} />
              Availability
            </div>
            <Badge type={isValid ? "success" : "danger"}>{isValid ? "Available" : "Insufficient"}</Badge>
          </div>
          <div className="sub-panel-body">
            <div className="stat-block">
              <div className="stat-label">Available</div>
              <div className="stat-value-row">
                <CheckCircle2 size={18} color="#16a34a" />
                {available}
              </div>
            </div>
            <div className="stat-block">
              <div className="stat-label">Reserved</div>
              <div className="stat-value-row">
                <Lock size={18} color="#ea580c" />
                {sourceStats.reserved}
              </div>
            </div>
            <div className="stat-block">
              <div className="stat-label">Incoming</div>
              <div className="stat-value-row">
                <Truck size={18} color="#2563eb" />
                {sourceStats.incoming}
              </div>
            </div>
            <div className="stat-block">
              <div className="stat-label">Minimum</div>
              <div className="stat-value-row">
                <Info size={18} color="#64748b" />
                {items.find((i) => i.name === selectedItem)?.reorderPoint ?? 0}
              </div>
            </div>
          </div>
        </div>

        <hr className="divider" />

        <h2 className="card-title">Validation</h2>
        <div className="validation-box" style={{ marginBottom: 16 }}>
          <div className="validation-header">
            <div className="validation-header-left">
              <CheckCircle2 size={18} />
              <div>
                <p className="validation-title">
                  {isValid ? "Request is valid" : "Request exceeds available stock"}
                </p>
                <p className="validation-desc">
                  {isValid
                    ? "Requested quantity does not exceed available stock."
                    : `Requested: ${requestedNum} • Available: ${available}. Reduce the quantity or select a different item.`}
                </p>
              </div>
            </div>
            <span className="validation-pass">{isValid ? "PASS" : "BLOCKED"}</span>
          </div>
          <div className="kv-row">
            <span>Requested</span>
            <span>{requestedNum}</span>
          </div>
          <div className="kv-row">
            <span>Available</span>
            <span>{available}</span>
          </div>
          <div className="kv-row total">
            <span>Remaining after reservation</span>
            <span className="kv-value">
              <CheckCircle2 size={16} />
              {Math.max(available - requestedNum, 0)}
            </span>
          </div>
        </div>
        
        <button
          type="button"
          className="ch-btn ch-btn--outline"
          onClick={onAddItem}
          disabled={!isValid}
          style={{ marginBottom: 16 }}
        >
          <Plus size={16} />
          Add to Request List
        </button>

        <hr className="divider" />

        <h2 className="card-title">Items in Request ({itemsInRequest.length})</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Quantity</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {itemsInRequest.map((row, i) => (
              <tr key={`${row.itemId}-${i}`}>
                <td>{row.item}</td>
                <td>{row.quantity}</td>
                <td>
                  <Badge type="default">{row.status}</Badge>
                </td>
                <td>
                  <button
                    type="button"
                    className="ch-btn ch-btn--danger"
                    onClick={() => onRemove(i)}
                  >
                    <Trash2 size={16} />
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            {itemsInRequest.length === 0 && (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', color: '#64748b', padding: '24px 0' }}>
                  No items added to this request yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <hr className="divider" />

        <div className="confirm-footer">
          <div className="auto-note">
            <p className="auto-note-title">
              <Info size={16} className="auto-note-icon" style={{ color: "#64748b" }} />
              Automatic checks
            </p>
            <ul className="list">
              <li>Stock availability</li>
              <li>Project status</li>
              <li>Store permissions</li>
              <li>Duplicate item detection</li>
            </ul>
          </div>
          <div className="actions-row">
            <button type="button" className="ch-btn ch-btn--outline" onClick={onCancel}>
              <X size={16} />
              Cancel
            </button>
            <button
              type="button"
              className="ch-btn ch-btn--primary"
              onClick={onSubmitRequest}
              disabled={busy || itemsInRequest.length === 0}
              style={(busy || itemsInRequest.length === 0) ? { opacity: 0.5, cursor: "not-allowed" } : undefined}
            >
              <CheckCircle2 size={16} />
              {busy ? "Submitting Request…" : "Submit Request"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
