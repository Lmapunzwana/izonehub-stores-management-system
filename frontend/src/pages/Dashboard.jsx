import { useNavigate } from "react-router-dom";
import {
  AlertTriangle, Package, Activity, ShieldAlert, Boxes,
  TrendingDown, Warehouse, Clock, Lock, Snowflake, Truck, Store,
} from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import { useEffect, useState } from "react";
import { apiFetch } from "../api";

export default function Dashboard() {
  const navigate = useNavigate();
  const { items, projects, materialRequests, discrepancies, expectedReceipts, stores, user } = useAppData();
  const [sub, setSub] = useState(null);

  useEffect(() => {
    apiFetch("/api/subscription").then(setSub).catch(() => {});
  }, []);

  const isAdmin = user?.roles?.includes("SYSTEM_ADMINISTRATOR");
  const isCentral = user?.roles?.includes("CENTRAL_STORE_MANAGER");
  const isSite = user?.roles?.includes("SITE_STORE_MANAGER");

  // Stock totals
  const totalAvailable = items.reduce((s, i) => s + (Number(i.available) || 0), 0);
  const totalReserved  = items.reduce((s, i) => s + (Number(i.reserved)  || 0), 0);
  const totalIncoming  = items.reduce((s, i) => s + (Number(i.incoming)  || 0), 0);
  const totalFrozen    = items.reduce((s, i) => s + (Number(i.frozen)    || 0), 0);

  // Counts
  const activeProjects       = projects.filter(p => p.status === "Active").length;
  const pendingApprovals     = materialRequests.filter(r => r.status === "Pending Approval").length;
  const openDiscrepancies    = discrepancies.filter(d => d.status === "OPEN").length;
  const pendingGRNs          = expectedReceipts.filter(r => r.statusIndex === 1).length;
  const closingStores        = stores.filter(s => s.active && s.closing).length;

  // Store capacity
  const operationalCount     = sub?.operationalCount ?? stores.filter(s => s.active).length;
  const allowedSlots         = sub?.allowedStoreSlots ?? 0;
  const capacityPct          = allowedSlots > 0 ? Math.round((operationalCount / allowedSlots) * 100) : 0;

  // Alert lists
  const lowStockItems        = items.filter(i => i.status?.label === "Low Stock").slice(0, 5);
  const openDiscList         = discrepancies.filter(d => d.status === "OPEN").slice(0, 4);
  const closingStoreList     = stores.filter(s => s.active && s.closing).slice(0, 3);

  const stats = [
    { label: "Available Stock", value: totalAvailable.toLocaleString(), icon: <Package size={20} />, color: "#10b981", bg: "#d1fae5" },
    { label: "Reserved Stock",  value: totalReserved.toLocaleString(),  icon: <Lock size={20} />,    color: "#f59e0b", bg: "#fef3c7" },
    { label: "Incoming Stock",  value: totalIncoming.toLocaleString(),  icon: <Truck size={20} />,   color: "#2563eb", bg: "#dbeafe" },
    { label: "Frozen Stock",    value: totalFrozen.toLocaleString(),    icon: <Snowflake size={20} />, color: "#7c3aed", bg: "#ede9fe" },
  ];

  return (
    <div className="dash-page">
      {/* Primary Stats Row */}
      <div className="dash-stats-grid">
        {stats.map(s => (
          <div key={s.label} className="dash-stat-item">
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
              <span style={{ padding: 8, background: s.bg, borderRadius: 8, color: s.color, display: "flex" }}>
                {s.icon}
              </span>
              <span className="dash-stat-label">{s.label}</span>
            </div>
            <div className="dash-stat-value" style={{ color: s.color }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* Secondary Metric Row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 16, marginBottom: 24 }}>
        <MetricPill label="Active Projects"    value={activeProjects}    color="#0f172a" onClick={() => navigate("/projects")} />
        <MetricPill label="Pending Approvals"  value={pendingApprovals}  color={pendingApprovals > 0 ? "#dc2626" : "#10b981"} onClick={() => navigate("/material-requests")} />
        <MetricPill label="Pending GRNs"       value={pendingGRNs}       color={pendingGRNs > 0 ? "#f59e0b" : "#10b981"} onClick={() => navigate("/expected-receipts")} />
        <MetricPill label="Open Discrepancies" value={openDiscrepancies} color={openDiscrepancies > 0 ? "#dc2626" : "#10b981"} onClick={() => navigate("/discrepancies")} />
        {(isAdmin || isCentral) && allowedSlots > 0 && (
          <div
            className="dash-stat-item"
            style={{ cursor: "pointer", position: "relative" }}
            onClick={() => navigate(isAdmin ? "/subscription" : "/stores")}
          >
            <div className="dash-stat-label" style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <Store size={15} /> Store Capacity
            </div>
            <div style={{ fontSize: "1.4em", fontWeight: 700, color: capacityPct >= 100 ? "#dc2626" : "#0f172a", margin: "4px 0" }}>
              {operationalCount} / {allowedSlots}
            </div>
            <div style={{ height: 6, background: "#e2e8f0", borderRadius: 4, overflow: "hidden" }}>
              <div style={{
                height: "100%", borderRadius: 4,
                width: `${Math.min(capacityPct, 100)}%`,
                background: capacityPct >= 100 ? "#dc2626" : capacityPct >= 80 ? "#f59e0b" : "#10b981",
                transition: "width 0.5s ease",
              }} />
            </div>
            <div className="dash-stat-sub" style={{ color: "#64748b", marginTop: 4 }}>
              {allowedSlots - operationalCount} slot{allowedSlots - operationalCount !== 1 ? "s" : ""} available
            </div>
          </div>
        )}
      </div>

      <div className="two-col">
        {/* Critical Alerts */}
        <div className="dash-card">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 20px 0", display: "flex", alignItems: "center", gap: 8 }}>
            <ShieldAlert size={20} color="#ef4444" /> Critical Alerts
          </h3>
          <div className="dash-alert-list">
            {lowStockItems.length === 0 && openDiscList.length === 0 && pendingApprovals === 0 && closingStoreList.length === 0 && (
              <div style={{ color: "#64748b", padding: "16px 0" }}>No critical alerts at this time.</div>
            )}

            {closingStoreList.map(store => (
              <div key={store.id} className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/stores")}>
                <div style={{ padding: 8, background: "#fef3c7", borderRadius: 8, color: "#d97706" }}>
                  <Warehouse size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 15, marginBottom: 4 }}>Store Closing: {store.name}</div>
                  <div style={{ color: "#64748b", fontSize: 13 }}>Pending shutdown — outstanding transactions must be resolved.</div>
                </div>
              </div>
            ))}

            {lowStockItems.map(item => (
              <div key={item.id} className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/items")}>
                <div style={{ padding: 8, background: "#fee2e2", borderRadius: 8, color: "#dc2626" }}>
                  <TrendingDown size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 15, marginBottom: 4 }}>Low Stock: {item.name}</div>
                  <div style={{ color: "#64748b", fontSize: 13 }}>Only {item.available} units remaining (threshold: {item.reorderPoint}).</div>
                </div>
              </div>
            ))}

            {openDiscList.map(disc => (
              <div key={disc.id} className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/discrepancies")}>
                <div style={{ padding: 8, background: "#fef3c7", borderRadius: 8, color: "#d97706" }}>
                  <Boxes size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 15, marginBottom: 4 }}>Open Discrepancy: {disc.item?.name || "Unknown"}</div>
                  <div style={{ color: "#64748b", fontSize: 13 }}>Frozen: {disc.frozenQuantity} units awaiting resolution.</div>
                </div>
              </div>
            ))}

            {pendingApprovals > 0 && (
              <div className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/material-requests")}>
                <div style={{ padding: 8, background: "#dbeafe", borderRadius: 8, color: "#2563eb" }}>
                  <Activity size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 15, marginBottom: 4 }}>Pending Material Requests</div>
                  <div style={{ color: "#64748b", fontSize: 13 }}>{pendingApprovals} request{pendingApprovals !== 1 ? "s" : ""} awaiting approval.</div>
                </div>
              </div>
            )}

            {pendingGRNs > 0 && (isCentral || isAdmin) && (
              <div className="dash-alert-item" style={{ cursor: "pointer" }} onClick={() => navigate("/expected-receipts")}>
                <div style={{ padding: 8, background: "#f0fdf4", borderRadius: 8, color: "#16a34a" }}>
                  <Clock size={20} />
                </div>
                <div>
                  <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 15, marginBottom: 4 }}>Pending GRN Confirmations</div>
                  <div style={{ color: "#64748b", fontSize: 13 }}>{pendingGRNs} receipt{pendingGRNs !== 1 ? "s" : ""} awaiting goods received confirmation.</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="dash-card darker">
          <h3 className="card-title" style={{ color: "#0f172a", margin: "0 0 20px 0" }}>Quick Actions</h3>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/items/add-item")}>
                Add New Item
              </button>
            )}
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/expected-receipts")}>
                New Expected Receipt
              </button>
            )}
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/material-requests")}>
                Review Requests
              </button>
            )}
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/dispatch")}>
                Issues &amp; Dispatch
              </button>
            )}
            {isSite && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/material-requests/add-item")}>
                New Material Request
              </button>
            )}
            {isSite && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/returns")}>
                Return Items
              </button>
            )}
            {isSite && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/stock-counts")}>
                Start Stock Count
              </button>
            )}
            {isSite && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/consumption")}>
                Log Consumption
              </button>
            )}
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/discrepancies")}>
                View Discrepancies
              </button>
            )}
            {(isAdmin || isCentral) && (
              <button className="ch-btn ch-btn--outline" style={{ justifyContent: "center", padding: 16 }} onClick={() => navigate("/reports")}>
                Reports
              </button>
            )}
          </div>

          {/* Top Active Projects */}
          {projects.filter(p => p.status === "Active").length > 0 && (
            <div style={{ marginTop: 24, borderTop: "1px solid #e5e7eb", paddingTop: 16 }}>
              <div style={{ fontWeight: 600, color: "#0f172a", marginBottom: 12, fontSize: 14 }}>Active Projects</div>
              {projects.filter(p => p.status === "Active").slice(0, 4).map(proj => (
                <div
                  key={proj.id}
                  style={{ marginBottom: 10, cursor: "pointer", paddingBottom: 10, borderBottom: "1px solid #f1f5f9", display: "flex", justifyContent: "space-between", alignItems: "center" }}
                  onClick={() => navigate(`/projects/${proj.id}`)}
                >
                  <div>
                    <div style={{ fontWeight: 600, color: "#0f172a", fontSize: 14 }}>{proj.name}</div>
                    <div style={{ fontSize: 12, color: "#64748b" }}>Manager: {proj.manager}</div>
                  </div>
                  {proj.budget > 0 && (
                    <span style={{ color: "#64748b", fontWeight: 600, fontSize: 13 }}>${Number(proj.budget).toLocaleString()}</span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function MetricPill({ label, value, color, onClick }) {
  return (
    <div
      className="dash-stat-item"
      style={{ cursor: onClick ? "pointer" : "default" }}
      onClick={onClick}
    >
      <div className="dash-stat-label">{label}</div>
      <div className="dash-stat-value" style={{ color }}>{value}</div>
    </div>
  );
}
