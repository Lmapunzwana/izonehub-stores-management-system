import { Truck, CheckCircle, AlertTriangle } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { useAppData } from "../context/AppDataContext";

// Real metrics from GET /api/reports/supplier-performance, computed by
// SupplierPerformanceService from actual GRN history (accuracy = % of lines
// received in full at GOOD condition, defect rate = % DAMAGED, fulfillment =
// total received / total expected, lead time = avg days between the
// receipt being raised and the GRN being confirmed). Suppliers with zero
// GRN history simply won't appear here — there's nothing to compute yet.
export default function SupplierPerformancePage() {
  const { supplierPerformance } = useAppData();

  const overallAccuracy = supplierPerformance.length
    ? (
        supplierPerformance.reduce((s, x) => s + Number(x.accuracyPercent), 0) / supplierPerformance.length
      ).toFixed(1)
    : "—";
  const overallLeadTime = supplierPerformance.length
    ? (
        supplierPerformance.reduce((s, x) => s + Number(x.avgLeadTimeDays), 0) / supplierPerformance.length
      ).toFixed(1)
    : "—";
  const maxLeadTime = Math.max(...supplierPerformance.map((s) => Number(s.avgLeadTimeDays)), 1);
  const BAR_COLORS = ["#2563eb", "#7c3aed", "#ea580c", "#16a34a", "#dc2626"];

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Supplier Performance"
          subtitle="Computed live from GRN receiving history — not a manually maintained score."
        />
        <div className="stats-grid">
          <div className="card stat-card">
            <div className="stat-label">Suppliers with History</div>
            <div className="stat-value blue">{supplierPerformance.length}</div>
          </div>
          <div className="card stat-card">
            <div className="stat-label">Avg Accuracy</div>
            <div className="stat-value green">{overallAccuracy}%</div>
          </div>
          <div className="card stat-card">
            <div className="stat-label">Avg Lead Time</div>
            <div className="stat-value orange">{overallLeadTime}d</div>
          </div>
        </div>

        {supplierPerformance.length === 0 && (
          <p style={{ color: "#64748b", marginTop: 16 }}>
            No confirmed GRNs yet — performance can only be computed once at least one receipt has been
            received and confirmed.
          </p>
        )}
      </div>

      {supplierPerformance.length > 0 && (
        <div className="card">
          <CardHeader title="Lead Time by Supplier" right={<span className="ch-chip">Days</span>} />
          {supplierPerformance.map((s, i) => (
            <div className="bar-row" key={s.supplierName}>
              <div className="bar-row-label">
                <span>
                  <Truck size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
                  {s.supplierName}
                </span>
                <span>{s.avgLeadTimeDays} days</span>
              </div>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{
                    width: `${(Number(s.avgLeadTimeDays) / maxLeadTime) * 100}%`,
                    background: BAR_COLORS[i % BAR_COLORS.length],
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      {supplierPerformance.length > 0 && (
        <div className="card">
          <CardHeader title="Supplier Leaderboard" />
          <table className="table">
            <thead>
              <tr>
                <th>Supplier</th>
                <th>GRNs Received</th>
                <th>Accuracy</th>
                <th>Fulfillment</th>
                <th>Defect Rate</th>
                <th>Avg Lead Time</th>
              </tr>
            </thead>
            <tbody>
              {supplierPerformance.map((s) => (
                <tr key={s.supplierName}>
                  <td>
                    <div className="item-cell">
                      <span className="item-icon">
                        <Truck size={18} />
                      </span>
                      <div className="item-name">{s.supplierName}</div>
                    </div>
                  </td>
                  <td>{s.ordersCount}</td>
                  <td>
                    {Number(s.accuracyPercent) >= 90 ? (
                      <CheckCircle size={14} color="#16a34a" style={{ verticalAlign: "-2px", marginRight: 4 }} />
                    ) : (
                      <AlertTriangle size={14} color="#ea580c" style={{ verticalAlign: "-2px", marginRight: 4 }} />
                    )}
                    {s.accuracyPercent}%
                  </td>
                  <td>{s.fulfillmentPercent}%</td>
                  <td>{s.defectRatePercent}%</td>
                  <td>{s.avgLeadTimeDays} days</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {supplierPerformance.some((s) => s.accuracyTrend?.length > 0) && (
        <div className="card">
          <CardHeader title="Accuracy Trend by Supplier" right={<span className="ch-chip">By month received</span>} />
          {supplierPerformance
            .filter((s) => s.accuracyTrend?.length > 0)
            .map((s) => {
              const maxVal = Math.max(...s.accuracyTrend.map((t) => Number(t.accuracyPercent)), 1);
              return (
                <div key={s.supplierName} style={{ marginBottom: 20 }}>
                  <p style={{ fontWeight: 600, marginBottom: 8 }}>{s.supplierName}</p>
                  <div className="vbar-chart" style={{ height: 100 }}>
                    {s.accuracyTrend.map((t) => (
                      <div className="vbar-col" key={t.month}>
                        <div
                          className="vbar"
                          style={{ height: `${(Number(t.accuracyPercent) / maxVal) * 100}%`, background: "#16a34a" }}
                        />
                        <span className="vbar-label">{t.month}</span>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}
