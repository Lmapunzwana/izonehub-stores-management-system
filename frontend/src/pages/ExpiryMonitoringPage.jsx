import { CalendarClock, AlertTriangle } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

function tierBadge(tier) {
  if (tier === 30) return { label: "Critical — 30 days", type: "danger" };
  if (tier === 60) return { label: "Warning — 60 days", type: "warning" };
  if (tier === 90) return { label: "Monitor — 90 days", type: "info" };
  return { label: "OK", type: "success" };
}

export default function ExpiryMonitoringPage() {
  const { expiryItems } = useAppData();
  const critical = expiryItems.filter((e) => e.tier === 30);
  const sorted = [...expiryItems].sort((a, b) => a.daysRemaining - b.daysRemaining);

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          icon={<CalendarClock size={20} />}
          title="Expiry Monitoring"
          subtitle="Alerts generated automatically at 90 / 60 / 30 days remaining"
          status={{
            label: `${critical.length} critical`,
            variant: critical.length > 0 ? "danger" : "success",
          }}
        />

        {critical.length > 0 && (
          <div className="alert-banner" style={{ marginBottom: 20 }}>
            <AlertTriangle size={22} className="alert-banner-icon" />
            <div className="alert-banner-text">
              <p className="alert-banner-title">
                {critical.length} batch{critical.length > 1 ? "es" : ""} within 30 days of expiry
              </p>
              <p className="alert-banner-desc">
                {critical.map((c) => `${c.item} (${c.batchNo})`).join(" · ")} — prioritize for consumption or return.
              </p>
            </div>
          </div>
        )}

        <table className="table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Batch No</th>
              <th>Quantity</th>
              <th>Expiry Date</th>
              <th>Days Remaining</th>
              <th>Alert</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((e) => {
              const badge = tierBadge(e.tier);
              return (
                <tr key={e.batchNo}>
                  <td>{e.item}</td>
                  <td>{e.batchNo}</td>
                  <td>{e.quantity}</td>
                  <td>{e.expiryDate}</td>
                  <td>{e.daysRemaining} days</td>
                  <td>
                    <Badge type={badge.type}>{badge.label}</Badge>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
