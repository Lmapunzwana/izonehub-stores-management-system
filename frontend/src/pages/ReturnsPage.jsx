import { useState } from "react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { CheckCircle2, RotateCcw } from "lucide-react";
import { useAppData } from "../context/AppDataContext";
import ReturnToCentralModal from "../components/ReturnToCentralModal";

export default function ReturnsPage() {
  const { returnsList, confirmReturn, refreshItems } = useAppData();
  const [modalOpen, setModalOpen] = useState(false);
  const awaitingCount = returnsList.filter((r) => r.status === "Awaiting Confirmation").length;

  return (
    <div className="page">
      <ReturnToCentralModal 
        isOpen={modalOpen} 
        onClose={() => setModalOpen(false)} 
        onSuccess={() => {
          refreshItems();
          // Ideally fetch returns list again, but refreshItems might do enough depending on AppDataContext
        }} 
      />
      <div className="card">
        <CardHeader
          title="Project Returns"
          actions={[
            {
              label: "New Return to Central",
              icon: <RotateCcw size={16} />,
              variant: "primary",
              onClick: () => setModalOpen(true),
            },
          ]}
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
