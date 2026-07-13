import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { CheckCircle2 } from "lucide-react";
import { useAppData } from "../context/AppDataContext";

export default function ReturnsPage() {
  const { returnsList, confirmReturn } = useAppData();
  const awaitingCount = returnsList.filter((r) => r.status === "Awaiting Confirmation").length;

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Project Returns"
          status={{
            label: `${awaitingCount} awaiting confirmation`,
            variant: awaitingCount > 0 ? "warning" : "success",
          }}
        />
        <table className="table">
          <thead>
            <tr>
              <th>Return No</th>
              <th>Project</th>
              <th>Items</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {returnsList.map((r) => (
              <tr key={r.returnNo}>
                <td>{r.returnNo}</td>
                <td>{r.project}</td>
                <td>
                  {r.original.lines?.map((l) => `${l.item.name} × ${l.quantity}`).join(", ") || "—"}
                </td>
                <td>
                  <Badge type={r.status === "Confirmed" ? "success" : "warning"}>{r.status}</Badge>
                </td>
                <td>
                  {r.status === "Awaiting Confirmation" ? (
                    <button className="btn btn-success" onClick={() => confirmReturn(r.returnNo)}>
                      <CheckCircle2 />
                      Confirm Return
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
