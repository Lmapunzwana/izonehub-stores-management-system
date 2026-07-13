import { Navigate, useLocation } from "react-router-dom";
import { useAppData } from "../context/AppDataContext";

export default function RequireAuth({ children }) {
  const { user, loading } = useAppData();
  const location = useLocation();

  if (loading) {
    return <div className="page" style={{ padding: "40px" }}>Loading Application...</div>;
  }

  if (!user) {
    // Redirect them to the /login page, but save the current location they were trying to go to
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
