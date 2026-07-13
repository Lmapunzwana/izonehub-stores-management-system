import { useNavigate } from "react-router-dom";
import { RefreshCw, Home, Zap } from "lucide-react";

export default function ServerErrorPage() {
  const navigate = useNavigate();
  const errorId = `ERR-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-${String(
    Math.floor(Math.random() * 9999)
  ).padStart(4, "0")}`;
  const time = new Date().toISOString().slice(0, 16).replace("T", " ");

  return (
    <div className="page">
      <div className="card error-card">
        <div className="error-icon error-icon--danger">
          <Zap size={32} />
        </div>
        <div className="error-code">500</div>
        <h2 className="error-title">Something Went Wrong</h2>
        <p className="error-desc">The server encountered an unexpected error while processing your request.</p>
        <p className="error-desc">Our team has been notified automatically.</p>
        <div className="error-actions">
          <button className="ch-btn ch-btn--primary" onClick={() => window.location.reload()}>
            <RefreshCw size={16} />
            Retry
          </button>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate("/")}>
            <Home size={16} />
            Return to Dashboard
          </button>
        </div>
        <div className="error-meta">
          <div className="error-meta-row">
            <span>Error ID</span>
            <span className="error-meta-value">{errorId}</span>
          </div>
          <div className="error-meta-row">
            <span>Time</span>
            <span className="error-meta-value">{time}</span>
          </div>
          <div className="error-meta-row">
            <span>Status</span>
            <span className="badge info">Automatically Logged</span>
          </div>
        </div>
      </div>
    </div>
  );
}
