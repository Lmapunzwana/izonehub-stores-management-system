import { useNavigate } from "react-router-dom";
import { Home, ArrowLeft, SearchX } from "lucide-react";

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <div className="page">
      <div className="card error-card">
        <div className="error-icon error-icon--info">
          <SearchX size={32} />
        </div>
        <div className="error-code">404</div>
        <h2 className="error-title">Page Not Found</h2>
        <p className="error-desc">The page you are looking for does not exist.</p>
        <p className="error-desc">Check the URL or navigate back to the dashboard.</p>
        <div className="error-actions">
          <button className="ch-btn ch-btn--primary" onClick={() => navigate("/")}>
            <Home size={16} />
            Go Home
          </button>
          <button className="ch-btn ch-btn--outline" onClick={() => navigate(-1)}>
            <ArrowLeft size={16} />
            Back
          </button>
        </div>
      </div>
    </div>
  );
}
