import { useNavigate } from "react-router-dom";
import { Mail, Home, ShieldAlert } from "lucide-react";

export default function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <div className="page">
      <div className="card error-card">
        <div className="error-icon error-icon--warning">
          <ShieldAlert size={32} />
        </div>
        <div className="error-code">403</div>
        <h2 className="error-title">Access Denied</h2>
        <p className="error-desc">You do not have permission to access this page.</p>
        <p className="error-desc">Contact your system administrator if you believe this is an error.</p>
        <div className="error-actions">
          <button
            className="ch-btn ch-btn--primary"
            onClick={() => alert("Access request sent to your system administrator.")}
          >
            <Mail size={16} />
            Request Access
          </button>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate("/")}>
            <Home size={16} />
            Return to Dashboard
          </button>
        </div>
      </div>
    </div>
  );
}
