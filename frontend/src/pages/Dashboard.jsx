import { useNavigate } from "react-router-dom";
import { 
  AlertTriangle, Package, Activity, ArrowUpRight, ArrowDownRight, 
  BarChart3, Settings, ShieldAlert, Boxes, TrendingUp 
} from "lucide-react";
import { useAppData } from "../context/AppDataContext";

export default function Dashboard() {
  const navigate = useNavigate();
  const { items, projects, materialRequests, discrepancies, expectedReceipts, supplierPerformance } = useAppData();

  // Real Data Computations
  const totalItemsAvailable = items.reduce((sum, item) => sum + (item.available || 0), 0);
  const activeProjects = projects.filter(p => p.status === "Active").length;
  const openRequestsCount = materialRequests.filter(r => r.status === "Pending Approval" || r.status === "Approved").length;
  const openDiscrepanciesCount = discrepancies.filter(d => d.status === "OPEN").length;

  const lowStockItems = items.filter(i => i.status?.label === "Low Stock").slice(0, 5);
  const openDiscrepancies = discrepancies.filter(d => d.status === "OPEN").slice(0, 5);
  const topProjects = [...projects].sort((a, b) => b.budget - a.budget).slice(0, 4);

  return (
    <div className="dash-page">
      {/* Top row: 4 key metrics */}
      <div className="dash-stats-grid">
        <div className="dash-stat-item">
          <div className="dash-stat-label">
            Total Stock Units <Package size={16} />
          </div>
          <div className="dash-stat-value">
            {totalItemsAvailable.toLocaleString()}
          </div>
          <div className="dash-stat-sub" style={{ color: "#64748b" }}>
            Across all locations
          </div>
        </div>
        
        <div className="dash-stat-item">
          <div className="dash-stat-label">
            Active Projects <Activity size={16} />
          </div>
          <div className="dash-stat-value">{activeProjects}</div>
          <div className="dash-stat-sub" style={{ color: "#64748b" }}>
            Currently managing
          </div>
        </div>
        
        <div className="dash-stat-item">
          <div className="dash-stat-label">
            Open Material Requests <BarChart3 size={16} />
          </div>
          <div className="dash-stat-value">{openRequestsCount}</div>
          <div className="dash-stat-sub" style={{ color: openRequestsCount > 0 ? "#ef4444" : "#10b981" }}>
            {openRequestsCount > 0 ? <><AlertTriangle size={14} style={{ display: "inline", verticalAlign: "middle" }} /> Action needed</> : "All caught up"}
          </div>
        </div>
        
        <div className="dash-stat-item">
          <div className="dash-stat-label">
            Open Discrepancies <ShieldAlert size={16} />
          </div>
          <div className="dash-stat-value">{openDiscrepanciesCount}</div>
          <div className="dash-stat-sub" style={{ color: openDiscrepanciesCount > 0 ? "#ef4444" : "#10b981" }}>
            {openDiscrepanciesCount > 0 ? "Requires investigation" : "Validation complete"}
          </div>
        </div>
      </div>

      <div className="two-col">
        {/* Critical Alerts */}
        <div className="dash-card">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 24px 0", display: "flex", alignItems: "center", gap: "8px" }}>
            <ShieldAlert size={20} color="#ef4444" /> Critical Alerts
          </h3>
          <div className="dash-alert-list">
            {lowStockItems.length === 0 && openDiscrepancies.length === 0 && openRequestsCount === 0 && (
              <div style={{ color: "#64748b", padding: "16px 0" }}>No critical alerts at this time.</div>
            )}
            
            {lowStockItems.map(item => (
              <div key={item.id} className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/items")}>
                <div style={{ padding: "8px", background: "#fee2e2", borderRadius: "8px", color: "#dc2626" }}>
                  <TrendingUp size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: "15px", marginBottom: "4px" }}>Low Stock: {item.name}</div>
                  <div style={{ color: "#64748b", fontSize: "13px" }}>Only {item.available} units remaining. Reorder threshold is {item.reorderPoint}.</div>
                </div>
              </div>
            ))}
            
            {openDiscrepancies.map(disc => (
              <div key={disc.id} className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/discrepancies")}>
                <div style={{ padding: "8px", background: "#fef3c7", borderRadius: "8px", color: "#d97706" }}>
                  <Boxes size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: "15px", marginBottom: "4px" }}>Discrepancy: {disc.item?.name || "Unknown"}</div>
                  <div style={{ color: "#64748b", fontSize: "13px" }}>Reported discrepancy of {disc.dispatchedQuantity - disc.receivedQuantity} units.</div>
                </div>
              </div>
            ))}

            {openRequestsCount > 0 && (
              <div className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/material-requests")}>
                <div style={{ padding: "8px", background: "#dbeafe", borderRadius: "8px", color: "#2563eb" }}>
                  <Activity size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: "15px", marginBottom: "4px" }}>Pending Material Requests</div>
                  <div style={{ color: "#64748b", fontSize: "13px" }}>There are {openRequestsCount} requests awaiting approval or dispatch.</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Top Active Projects */}
        <div className="dash-card darker">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 20px 0" }}>Largest Projects by Budget</h3>
          <div className="dash-alert-list">
            {topProjects.length === 0 && <div style={{ color: "#64748b" }}>No active projects found.</div>}
            {topProjects.map(proj => (
              <div key={proj.id} style={{ marginBottom: "12px", cursor: "pointer", paddingBottom: "12px", borderBottom: "1px solid #e5e7eb" }} onClick={() => navigate(`/projects/${proj.id}`)}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: "14px", marginBottom: "4px" }}>
                  <span style={{ fontWeight: 600, color: "#0f172a" }}>{proj.name}</span>
                  <span style={{ color: "#64748b", fontWeight: 600 }}>${(proj.budget || 0).toLocaleString()}</span>
                </div>
                <div style={{ fontSize: "13px", color: "#64748b" }}>Manager: {proj.manager}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="two-col">
        {/* Supplier Performance */}
        <div className="dash-card">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 20px 0" }}>Supplier Performance (Recent)</h3>
          
          <div className="dash-perf-grid">
            {supplierPerformance && supplierPerformance.length > 0 ? supplierPerformance.slice(0, 4).map(perf => (
              <div key={perf.supplierName} className="dash-perf-item" style={{ padding: "12px", border: "1px solid #e5e7eb", borderRadius: "8px" }}>
                <div className="label" style={{ color: "#0f172a", fontWeight: 600 }}>{perf.supplierName}</div>
                <div style={{ display: "flex", justifyContent: "space-between", marginTop: "8px" }}>
                  <div style={{ fontSize: "13px", color: "#64748b" }}>On Time: <span style={{ color: perf.onTimeDeliveryRate >= 90 ? "#16a34a" : "#ea580c", fontWeight: 600 }}>{perf.onTimeDeliveryRate?.toFixed(1)}%</span></div>
                  <div style={{ fontSize: "13px", color: "#64748b" }}>Quality: <span style={{ color: perf.qualityAcceptanceRate >= 90 ? "#16a34a" : "#ea580c", fontWeight: 600 }}>{perf.qualityAcceptanceRate?.toFixed(1)}%</span></div>
                </div>
              </div>
            )) : (
              <div style={{ color: "#64748b", gridColumn: "1 / -1" }}>No supplier performance data available.</div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="dash-card darker">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 20px 0" }}>Quick Actions</h3>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
            <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: "16px" }} onClick={() => navigate("/items/add-item")}>
              Add New Item
            </button>
            <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: "16px" }} onClick={() => navigate("/material-requests")}>
              Material Requests
            </button>
            <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: "16px" }} onClick={() => navigate("/expected-receipts")}>
              Expected Receipts
            </button>
            <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: "16px" }} onClick={() => navigate("/dispatch")}>
              Issues & Dispatch
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
