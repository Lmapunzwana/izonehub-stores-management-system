import { useNavigate } from "react-router-dom";
import { LogIn, Home, Lock } from "lucide-react";

export default function UnauthorizedPage() {
  const navigate = useNavigate();
  return (
    <div className="page">
      <div className="card error-card">
        <div className="error-icon error-icon--danger">
          <Lock size={32} />
        </div>
        <div className="error-code">401</div>
        <h2 className="error-title">Authentication Required</h2>
        <p className="error-desc">Your session has expired or you are not signed in.</p>
        <p className="error-desc">Please authenticate to continue.</p>
        <div className="error-actions">
          <button className="ch-btn ch-btn--primary" onClick={() => navigate("/login")}>
            <LogIn size={16} />
            Sign In
          </button>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate("/")}>
            <Home size={16} />
            Back to Home
          </button>
        </div>
      </div>
    </div>
  );
}
