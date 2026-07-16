import { useState } from "react";
import CardHeader from "../components/CardHeader";
import { BarChart } from "lucide-react";
import { apiFetch } from "../api";

export default function ReportsPage() {
  const [reportType, setReportType] = useState("Inventory Summary");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [status, setStatus] = useState(null);

  function download(content, filename, type) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  function getQueryString() {
    const params = new URLSearchParams();
    if (startDate) params.append("from", startDate);
    if (endDate) params.append("to", endDate);
    return params.toString();
  }

  function getEndpoint() {
    if (reportType === "Inventory Summary") return "/api/reports/current-stock";
    if (reportType === "Project Consumption") return "/api/reports/project-consumption";
    if (reportType === "GRN Report") return "/api/reports/grns";
    if (reportType === "Discrepancy Report") return "/api/reports/discrepancies";
    return "/api/reports/current-stock";
  }

  function onGenerateReport() {
    setStatus(`Generated "${reportType}" report${startDate || endDate ? ` for the selected dates` : ""}.`);
  }

  async function onExportPDF() {
    try {
      const qs = getQueryString();
      const url = `${getEndpoint()}?format=pdf${qs ? "&" + qs : ""}`;
      const blob = await apiFetch(url);
      download(blob, `${reportType.toLowerCase().replace(" ", "-")}.pdf`, "application/pdf");
      setStatus(`Downloaded ${reportType} PDF`);
    } catch (e) {
      console.error(e);
      setStatus(`Failed to download ${reportType} PDF`);
    }
  }

  async function onExportCSV() {
    try {
      const qs = getQueryString();
      const url = `${getEndpoint()}?format=csv${qs ? "&" + qs : ""}`;
      const blob = await apiFetch(url);
      download(blob, `${reportType.toLowerCase().replace(" ", "-")}.csv`, "text/csv");
      setStatus(`Downloaded ${reportType} CSV`);
    } catch (e) {
      console.error(e);
      setStatus(`Failed to download ${reportType} CSV`);
    }
  }

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Reports"
          actions={[
            {
              label: "Generate Report",
              icon: <BarChart size={16} />,
              variant: "primary",
              onClick: onGenerateReport,
            },
          ]}
        />
        <div className="form-grid">
          <div>
            <label>Report Type</label>
            <select className="input" value={reportType} onChange={(e) => setReportType(e.target.value)}>
              <option>Inventory Summary</option>
              <option>Project Consumption</option>
              <option>GRN Report</option>
              <option>Discrepancy Report</option>
            </select>
          </div>

          <div>
            <label>Start Date</label>
            <input
              className="input"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          
          <div>
            <label>End Date</label>
            <input
              className="input"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>

          <div className="full actions-row">
            {status && (
              <span style={{ color: "#16a34a", fontWeight: 600, marginRight: "auto" }}>
                {status}
              </span>
            )}
            <button className="ch-btn ch-btn--outline" onClick={onExportPDF}>
              Export PDF
            </button>
            <button className="ch-btn ch-btn--primary" onClick={onExportCSV}>
              Export CSV
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
