import { Link, useLocation } from 'react-router-dom'
import { useAppData } from '../context/AppDataContext'

import { useNavigate } from 'react-router-dom'
import { apiFetch } from '../api'

// Define which roles can see which links. If not specified, everyone sees it.
const links = [
  { label: 'Dashboard', path: '/', badge: null },
  { label: 'Items', path: '/items', badge: 'items' },
  { label: 'Expected Receipts', path: '/expected-receipts', badge: 'expectedReceipts' },
  { label: 'Material Requests', path: '/material-requests', badge: 'materialRequests' },
  { label: 'Issues & Dispatch', path: '/dispatch', badge: 'dispatch', roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER'] },
  { label: 'Projects', path: '/projects', badge: null },
  { label: 'Stores', path: '/stores', badge: null, roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER'] },
  { label: 'Subscription', path: '/subscription', badge: null, roles: ['SYSTEM_ADMINISTRATOR'] },
  { label: 'Employees', path: '/employees', badge: null },
  { label: 'Users', path: '/users', badge: null, roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER'] },
  { label: 'Returns', path: '/returns', badge: 'returns' },
  { label: 'Discrepancies', path: '/discrepancies', badge: 'discrepancies', roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER'] },
  { label: 'Stock Counts', path: '/stock-counts', badge: 'stockCounts', roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER', 'SITE_STORE_MANAGER'] },
  { label: 'Expiry Monitoring', path: '/expiry-monitoring', badge: 'expiry' },
  { label: 'Audit Log', path: '/audit-log', badge: null, roles: ['SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER'] },
  { label: 'Reports', path: '/reports', badge: null },
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

  // Filter links based on user roles
  const userRoles = user?.roles || [];
  const visibleLinks = links.filter(link => {
    if (!link.roles) return true; // Everyone can see
    return link.roles.some(r => userRoles.includes(r));
  });

  const defaultStore = stores?.find(s => s.id === defaultStoreId);
  const operatingAs = defaultStore ? `Operating as: ${defaultStore.type === 'CENTRAL' ? 'Central Warehouse' : 'Site Store'} (${defaultStore.name})` : "Stores Management";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="logo" style={{ padding: '16px', textAlign: 'center' }}>
          <img src="/logo.jpeg" alt="Stores Management Logo" style={{ maxWidth: '100%', height: 'auto', maxHeight: '60px', objectFit: 'contain' }} />
        </div>
        {visibleLinks.map(({ label, path, badge }) => {
          const count = badge ? badges[badge] : 0
          return (
            <Link
              key={path}
              to={path}
              className={location.pathname === path ? 'nav-link active' : 'nav-link'}
            >
              <span>{label}</span>
              {count > 0 && <span className="nav-badge">{count}</span>}
            </Link>
          )
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
