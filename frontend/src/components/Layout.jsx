import { Link, useLocation } from 'react-router-dom'
import { useAppData } from '../context/AppDataContext'

import { useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'

// Nav link groups, shown with visual section separators.
// roles: which roles may see the link (omit for all roles).
const NAV_GROUPS = [
  {
    section: "Overview",
    links: [
      { label: "Dashboard",         path: "/",                badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
    ],
  },
  {
    section: "Warehouse",
    links: [
      { label: "Items",             path: "/items",           badge: "items",           roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
      { label: "Expected Receipts", path: "/expected-receipts", badge: "expectedReceipts", roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
      { label: "Issues & Dispatch", path: "/dispatch",        badge: "dispatch",        roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
      { label: "Returns",           path: "/returns",         badge: "returns",         roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
      { label: "Discrepancies",     path: "/discrepancies",   badge: "discrepancies",   roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
    ],
  },
  {
    section: "Operations",
    links: [
      { label: "Material Requests", path: "/material-requests", badge: "materialRequests" },
      { label: "Consumption",       path: "/consumption",     badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
      { label: "Stock Counts",      path: "/stock-counts",    badge: "stockCounts",     roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
    ],
  },
  {
    section: "Projects",
    links: [
      { label: "Projects",          path: "/projects",        badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"] },
    ],
  },
  {
    section: "Administration",
    links: [
      { label: "Stores",            path: "/stores",          badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
      { label: "Users",             path: "/users",           badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
      { label: "Employees",         path: "/employees",       badge: null },
      { label: "Subscription",      path: "/subscription",    badge: null,              roles: ["SYSTEM_ADMINISTRATOR"] },
      { label: "Audit Log",         path: "/audit-log",       badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
      { label: "Reports",           path: "/reports",         badge: null,              roles: ["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"] },
    ],
  },
]

export default function Layout({ children }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { badges, user, setUser, stores, defaultStoreId } = useAppData()

  const handleLogout = async () => {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } catch(e) { console.error(e) }
    setUser(null)
    navigate('/login')
  }

  const userRoles = user?.roles || [];

  function isLinkVisible(link) {
    if (!link.roles) return true;
    return link.roles.some(r => userRoles.includes(r));
  }

  const defaultStore = stores?.find(s => s.id === defaultStoreId);
  
  let operatingAs = "Stores Management";
  if (user?.assignedStore) {
    operatingAs = `Operating as: ${user.assignedStore.type === 'CENTRAL' ? 'Central Warehouse' : 'Site Store'} (${user.assignedStore.name})`;
  } else if (defaultStore) {
    operatingAs = `Operating as: ${defaultStore.type === 'CENTRAL' ? 'Central Warehouse' : 'Site Store'} (${defaultStore.name})`;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="logo" style={{ padding: '16px', textAlign: 'center' }}>
          <img src="/logo.jpeg" alt="Stores Management Logo" style={{ maxWidth: '100%', height: 'auto', maxHeight: '60px', objectFit: 'contain' }} />
        </div>
        {NAV_GROUPS.map(({ section, links }) => {
          const visible = links.filter(isLinkVisible);
          if (visible.length === 0) return null;
          return (
            <div key={section}>
              <div style={{ padding: '8px 16px 4px 16px', fontSize: 10, fontWeight: 700, letterSpacing: '0.08em', color: '#94a3b8', textTransform: 'uppercase' }}>
                {section}
              </div>
              {visible.map(({ label, path, badge }) => {
                const count = badge ? badges[badge] : 0;
                return (
                  <Link
                    key={path}
                    to={path}
                    className={location.pathname === path || (path !== '/' && location.pathname.startsWith(path)) ? 'nav-link active' : 'nav-link'}
                  >
                    <span>{label}</span>
                    {count > 0 && <span className="nav-badge">{count}</span>}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </aside>

      <div className="content-shell">
        <header className="topbar">
          <div className="topbar-title">{operatingAs}</div>
          <div className="role-switcher" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <span className="topbar-user" style={{ fontWeight: 600 }}>
              {user?.email?.split('@')[0] || "User"}
              <span style={{ fontWeight: 400, opacity: 0.7, marginLeft: '8px', fontSize: '0.85em' }}>
                ({user?.roles[0]?.replace(/_/g, " ")})
              </span>
            </span>
            <button onClick={handleLogout} className="btn btn-outline" style={{ padding: '4px 12px', fontSize: '0.9em' }}>
              Logout
            </button>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  )
}
