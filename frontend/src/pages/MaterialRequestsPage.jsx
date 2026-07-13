import { useNavigate } from "react-router-dom";
import { Plus, Check, X } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

const STATUS_TYPE = {
  "Pending Approval": "warning",
  Approved: "info",
  "In Transit": "warning",
  Received: "success",
  "Received (Discrepancy)": "danger",
  Rejected: "danger",
};

export default function MaterialRequestsPage() {
  const navigate = useNavigate();
  const { materialRequests, approveRequest, rejectRequest } = useAppData();

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Material Requests"
          actions={[
            {
              label: "New Request",
              icon: <Plus size={16} />,
              variant: "primary",
              onClick: () => navigate("/material-requests/add-item"),
            },
          ]}
        />
        <table className="table">
          <thead>
            <tr>
              <th>Request No</th>
              <th>Project</th>
              <th>Requested By</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {materialRequests.map((r) => (
              <tr key={r.requestNo}>
                <td>{r.requestNo}</td>
                <td>{r.project}</td>
                <td>{r.requestedBy}</td>
                <td>
                  <Badge type={STATUS_TYPE[r.status] || "default"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Pending Approval" ? (
                    <div className="action-buttons">
                      <button
                        type="button"
                        className="ch-btn ch-btn--success"
                        onClick={() => approveRequest(r.requestNo)}
                      >
                        <Check size={16} />
                        Approve
                      </button>
                      <button
                        type="button"
                        className="ch-btn ch-btn--danger"
                        onClick={() => rejectRequest(r.requestNo)}
                      >
                        <X size={16} />
                        Reject
                      </button>
                    </div>
                  ) : r.status === "Approved" || r.status === "In Transit" ? (
                    <button className="ch-btn ch-btn--outline" onClick={() => navigate("/dispatch")}>
                      Go to Dispatch
                    </button>
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
