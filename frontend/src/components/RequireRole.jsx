import { useAppData } from "../context/AppDataContext";
import ForbiddenPage from "../pages/errors/ForbiddenPage";

// Gates a route by the user's REAL roles from GET /api/auth/me (e.g.
// "SYSTEM_ADMINISTRATOR"), matching the exact backend Role enum names used
// everywhere else in the app (see Layout.jsx's nav filtering). This used to
// compare against a leftover client-side "role switcher" that had no
// connection to the backend and used a different string format entirely
// ("System Administrator" vs "SYSTEM_ADMINISTRATOR" from a naive
// find/replace) — meaning real system administrators were being shown the
// "Users" link in the sidebar but got a 403 the moment they clicked it.
// This is purely a UI convenience: the backend's @PreAuthorize on each
// endpoint is the actual enforcement, this just avoids flashing a page the
// API is going to reject anyway.
export default function RequireRole({ role, children }) {
  const { user } = useAppData();
  const userRoles = user?.roles || [];
  const required = Array.isArray(role) ? role : [role];
  const allowed = required.some((r) => userRoles.includes(r));
  return allowed ? children : <ForbiddenPage />;
}
