import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Users, DollarSign, LogOut, Heart, ShieldCheck } from 'lucide-react'
import { usePermissions } from '../hooks/usePermissions'
import { logoutSubAdmin } from '../api/adminApi'
import { jwtDecode } from 'jwt-decode'

export default function Sidebar() {
  const navigate    = useNavigate()
  const perms       = usePermissions()

  const isSubAdmin  = !perms.isSuperAdmin

  async function logout() {
    if (isSubAdmin) {
      try { await logoutSubAdmin() } catch { /* ignore */ }
    }
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_role')
    navigate('/login')
  }

  let displayName = ''
  try {
    const token = localStorage.getItem('admin_token')
    if (token) {
      const dec: any = jwtDecode(token)
      displayName = dec.name || dec.username || ''
    }
  } catch { /* ignore */ }

  const navItems = [
    { to: '/',        label: 'Dashboard',       icon: LayoutDashboard, show: true },
    { to: '/users',   label: 'User Management', icon: Users,           show: true },
    { to: '/revenue', label: 'Revenue',         icon: DollarSign,      show: perms.revenue || perms.isSuperAdmin },
    { to: '/admin-users', label: 'Admin Users', icon: ShieldCheck,     show: perms.canManageAdmins || perms.isSuperAdmin },
  ]

  return (
    <aside className="w-56 bg-[#1E1E35] flex flex-col h-full shrink-0">
      {/* Logo */}
      <div className="flex items-center gap-2 px-5 py-5 border-b border-white/10">
        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-pink-500 to-purple-600 flex items-center justify-center">
          <Heart size={16} className="text-white fill-white" />
        </div>
        <span className="text-white font-bold text-lg tracking-wide">AuraLink</span>
        <span className="text-xs text-purple-300 ml-auto font-medium">Admin</span>
      </div>

      {/* User badge */}
      {displayName && (
        <div style={{ padding: '10px 20px 0', fontSize: 11, color: '#8A8799' }}>
          Logged in as <span style={{ color: '#C9A96E', fontWeight: 600 }}>{displayName}</span>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.filter(n => n.show).map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                isActive
                  ? 'bg-gradient-to-r from-pink-600 to-purple-700 text-white shadow-lg'
                  : 'text-slate-300 hover:bg-white/10 hover:text-white'
              }`
            }
          >
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div className="px-3 pb-5">
        <button
          onClick={logout}
          className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm font-medium text-slate-400 hover:bg-white/10 hover:text-white transition-all"
        >
          <LogOut size={17} />
          Logout
        </button>
      </div>
    </aside>
  )
}
