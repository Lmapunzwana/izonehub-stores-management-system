import { useState } from "react";
import CardHeader from "../components/CardHeader";
import { BarChart } from "lucide-react";

export default function ReportsPage() {
  const [reportType, setReportType] = useState("Inventory Summary");
  const [dateRange, setDateRange] = useState("");
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

  function onGenerateReport() {
    setStatus(`Generated "${reportType}" report${dateRange ? ` for ${dateRange}` : ""}.`);
  }

  function onExportPDF() {
    if (reportType === "Inventory Summary") {
      window.open('/api/reports/current-stock?format=pdf', '_blank');
      setStatus("Downloaded Inventory Summary PDF");
      return;
    }
    onGenerateReport();
    download(
      `Report: ${reportType}\nDate Range: ${dateRange || "All time"}\n`,
      `${reportType.replace(/\s+/g, "-").toLowerCase()}.pdf`,
      "application/pdf"
    );
  }

  function onExportCSV() {
    if (reportType === "Inventory Summary") {
      window.open('/api/reports/current-stock?format=csv', '_blank');
      setStatus("Downloaded Inventory Summary CSV");
      return;
    }
    onGenerateReport();
    download(
      `Report,${reportType}\nDate Range,${dateRange || "All time"}\n`,
      `${reportType.replace(/\s+/g, "-").toLowerCase()}.csv`,
      "text/csv"
    );
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
            <label>Date Range</label>
            <input
              className="input"
              type="date"
              value={dateRange}
              onChange={(e) => setDateRange(e.target.value)}
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
