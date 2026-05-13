import { useEffect, useState } from 'react'
import {
  listAdminUsers, createAdminUser, updateAdminPermissions,
  lockAdminUser, unlockAdminUser, deactivateAdminUser,
  resetAdminPassword, getAllAdminSessions, revokeAdminSession,
  AdminUserEntry, AdminSessionEntry
} from '../api/adminApi'
import { usePermissions } from '../hooks/usePermissions'
import { useNavigate } from 'react-router-dom'
import { ShieldCheck, Lock, Unlock, Trash2, Key, RefreshCw, X, Plus } from 'lucide-react'

const S = {
  page:   { background: '#0A0A0F', minHeight: '100vh', padding: '24px 28px', color: '#F0EDE8', fontFamily: "'Outfit','Inter',sans-serif" } as React.CSSProperties,
  card:   { background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: 24, marginBottom: 24 } as React.CSSProperties,
  th:     { padding: '10px 14px', fontSize: 11, color: '#5C5A6E', fontWeight: 600, textTransform: 'uppercase' as const, letterSpacing: 1, textAlign: 'left' as const },
  td:     { padding: '12px 14px', fontSize: 13, color: '#C8C5D8', borderTop: '1px solid rgba(255,255,255,0.05)' },
  badge:  (color: string) => ({ display: 'inline-block', padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: color + '22', color }),
  btn:    (color: string) => ({ background: color + '18', border: `1px solid ${color}40`, color, borderRadius: 8, padding: '5px 10px', fontSize: 12, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 4 } as React.CSSProperties),
  input:  { width: '100%', background: '#1C1C28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, padding: '10px 14px', fontSize: 13, color: '#F0EDE8', fontFamily: 'inherit', outline: 'none', marginBottom: 12, boxSizing: 'border-box' } as React.CSSProperties,
  label:  { fontSize: 12, color: '#8A8799', display: 'block', marginBottom: 4 } as React.CSSProperties,
}

const DEFAULT_PERMS = {
  dashboard: { viewStats: true, viewRevenue: false, viewCharts: true, viewAds: false },
  users: { view: true, create: false, delete: false, viewChats: false, managePoints: false },
  revenue: false,
  canManageAdmins: false,
}

function PermissionEditor({ value, onChange }: { value: any; onChange: (v: any) => void }) {
  function toggle(path: string[]) {
    const next = JSON.parse(JSON.stringify(value))
    let obj = next
    for (let i = 0; i < path.length - 1; i++) obj = obj[path[i]]
    obj[path[path.length - 1]] = !obj[path[path.length - 1]]
    onChange(next)
  }

  const row = (label: string, path: string[]) => {
    let obj = value
    for (const k of path.slice(0, -1)) obj = obj[k]
    const checked = !!obj[path[path.length - 1]]
    return (
      <label key={label} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', marginBottom: 8 }}>
        <input type="checkbox" checked={checked} onChange={() => toggle(path)}
          style={{ accentColor: '#9B7FE8', width: 16, height: 16 }} />
        <span style={{ fontSize: 13, color: '#C8C5D8' }}>{label}</span>
      </label>
    )
  }

  return (
    <div style={{ background: '#1C1C28', borderRadius: 10, padding: 16 }}>
      <div style={{ fontSize: 12, color: '#5C5A6E', marginBottom: 12, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 1 }}>Dashboard</div>
      {row('View Stats',           ['dashboard', 'viewStats'])}
      {row('View Revenue',         ['dashboard', 'viewRevenue'])}
      {row('View Charts',          ['dashboard', 'viewCharts'])}
      {row('View Ads',             ['dashboard', 'viewAds'])}
      <div style={{ fontSize: 12, color: '#5C5A6E', margin: '14px 0 10px', fontWeight: 600, textTransform: 'uppercase', letterSpacing: 1 }}>Users</div>
      {row('View Users',           ['users', 'view'])}
      {row('Create Users',         ['users', 'create'])}
      {row('Delete Users',         ['users', 'delete'])}
      {row('View Chats',           ['users', 'viewChats'])}
      {row('Manage Points',        ['users', 'managePoints'])}
      <div style={{ fontSize: 12, color: '#5C5A6E', margin: '14px 0 10px', fontWeight: 600, textTransform: 'uppercase', letterSpacing: 1 }}>Other</div>
      {row('Access Revenue Page',  ['revenue'])}
      {row('Manage Admin Accounts',['canManageAdmins'])}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
      <div style={{ background: '#111118', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 20, width: '100%', maxWidth: 500, padding: 28, maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: '#F0EDE8' }}>{title}</h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#8A8799', cursor: 'pointer' }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

export default function AdminUsers() {
  const perms    = usePermissions()
  const navigate = useNavigate()
  const [admins,   setAdmins]   = useState<AdminUserEntry[]>([])
  const [sessions, setSessions] = useState<AdminSessionEntry[]>([])
  const [loading,  setLoading]  = useState(true)
  const [msg,      setMsg]      = useState('')

  // Modals
  const [showCreate,     setShowCreate]     = useState(false)
  const [editPermsFor,   setEditPermsFor]   = useState<AdminUserEntry | null>(null)
  const [resetPwdFor,    setResetPwdFor]    = useState<AdminUserEntry | null>(null)
  const [newPassword,    setNewPassword]    = useState('')
  const [editedPerms,    setEditedPerms]    = useState<any>(DEFAULT_PERMS)

  // Create form
  const [cUsername,    setCUsername]    = useState('')
  const [cPassword,    setCPassword]    = useState('')
  const [cDisplayName, setCDisplayName] = useState('')
  const [cPerms,       setCPerms]       = useState<any>(DEFAULT_PERMS)
  const [cLoading,     setCLoading]     = useState(false)

  const canManage = perms.isSuperAdmin || perms.canManageAdmins

  useEffect(() => {
    if (!canManage) { navigate('/'); return }
    load()
  }, [])

  async function load() {
    setLoading(true)
    try {
      const [a, s] = await Promise.all([listAdminUsers(), getAllAdminSessions()])
      setAdmins(a); setSessions(s)
    } catch { /* ignore */ }
    finally { setLoading(false) }
  }

  async function doCreate() {
    setCLoading(true)
    try {
      await createAdminUser(cUsername, cPassword, cDisplayName, JSON.stringify(cPerms))
      setShowCreate(false); setCUsername(''); setCPassword(''); setCDisplayName(''); setCPerms(DEFAULT_PERMS)
      setMsg('Admin user created.')
      load()
    } catch (e: any) {
      setMsg(e?.response?.data?.error || 'Create failed.')
    } finally { setCLoading(false) }
  }

  async function doUpdatePerms() {
    if (!editPermsFor) return
    try {
      await updateAdminPermissions(editPermsFor.id, JSON.stringify(editedPerms))
      setEditPermsFor(null); setMsg('Permissions updated. User must re-login.')
      load()
    } catch { setMsg('Update failed.') }
  }

  async function doResetPassword() {
    if (!resetPwdFor || !newPassword) return
    try {
      await resetAdminPassword(resetPwdFor.id, newPassword)
      setResetPwdFor(null); setNewPassword(''); setMsg('Password reset.')
    } catch { setMsg('Reset failed.') }
  }

  async function doLock(a: AdminUserEntry) {
    try { await lockAdminUser(a.id, 'Manual lock by super admin'); setMsg(`${a.displayName} locked.`); load() }
    catch { setMsg('Failed.') }
  }

  async function doUnlock(a: AdminUserEntry) {
    try { await unlockAdminUser(a.id); setMsg(`${a.displayName} unlocked.`); load() }
    catch { setMsg('Failed.') }
  }

  async function doDeactivate(a: AdminUserEntry) {
    if (!confirm(`Deactivate ${a.displayName}? They will not be able to log in.`)) return
    try { await deactivateAdminUser(a.id); setMsg(`${a.displayName} deactivated.`); load() }
    catch { setMsg('Failed.') }
  }

  async function doRevokeSession(id: string) {
    try { await revokeAdminSession(id); setMsg('Session revoked.'); load() }
    catch { setMsg('Failed.') }
  }

  if (!canManage) return null

  return (
    <div style={S.page}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <ShieldCheck size={22} color="#9B7FE8" />
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: '#F0EDE8' }}>Admin Users</h1>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button onClick={load} style={S.btn('#8A8799')}><RefreshCw size={14} /> Refresh</button>
          <button onClick={() => { setShowCreate(true); setMsg('') }} style={S.btn('#9B7FE8')}>
            <Plus size={14} /> New Admin
          </button>
        </div>
      </div>

      {msg && (
        <div style={{ background: 'rgba(78,207,160,0.1)', border: '1px solid rgba(78,207,160,0.25)', borderRadius: 10, padding: '10px 16px', marginBottom: 16, fontSize: 13, color: '#4ECFA0' }}>
          {msg}
        </div>
      )}

      {/* Admin list */}
      <div style={S.card}>
        <h2 style={{ margin: '0 0 16px', fontSize: 15, fontWeight: 600, color: '#F0EDE8' }}>Sub-admin Accounts</h2>
        {loading ? (
          <div style={{ color: '#5C5A6E', textAlign: 'center', padding: 40 }}>Loading...</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  {['Name', 'Username', 'Status', 'Sessions', 'Last Login', 'Actions'].map(h => (
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {admins.map(a => (
                  <tr key={a.id}>
                    <td style={S.td}><span style={{ color: '#F0EDE8', fontWeight: 500 }}>{a.displayName}</span></td>
                    <td style={S.td}>{a.username}</td>
                    <td style={S.td}>
                      {!a.isActive
                        ? <span style={S.badge('#E05C6B')}>Deactivated</span>
                        : a.isLocked
                          ? <span style={S.badge('#F59E0B')}>Locked</span>
                          : <span style={S.badge('#4ECFA0')}>Active</span>}
                    </td>
                    <td style={S.td}>{a.activeSessions}</td>
                    <td style={S.td}>{a.lastLoginAt ? new Date(a.lastLoginAt).toLocaleString() : '—'}</td>
                    <td style={S.td}>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        <button style={S.btn('#9B7FE8')} onClick={() => { setEditPermsFor(a); setEditedPerms(JSON.parse(a.permissionsJson || '{}')); }}>
                          <ShieldCheck size={12} /> Perms
                        </button>
                        <button style={S.btn('#C9A96E')} onClick={() => { setResetPwdFor(a); setNewPassword('') }}>
                          <Key size={12} /> Reset Pwd
                        </button>
                        {a.isLocked
                          ? <button style={S.btn('#4ECFA0')} onClick={() => doUnlock(a)}><Unlock size={12} /> Unlock</button>
                          : <button style={S.btn('#F59E0B')} onClick={() => doLock(a)}><Lock size={12} /> Lock</button>}
                        {a.isActive && (
                          <button style={S.btn('#E05C6B')} onClick={() => doDeactivate(a)}><Trash2 size={12} /> Deactivate</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {admins.length === 0 && (
                  <tr><td colSpan={6} style={{ ...S.td, textAlign: 'center', color: '#5C5A6E', padding: 40 }}>No admin accounts yet.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Active sessions */}
      <div style={S.card}>
        <h2 style={{ margin: '0 0 16px', fontSize: 15, fontWeight: 600, color: '#F0EDE8' }}>Active Sessions</h2>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {['Admin', 'Device', 'IP', 'Login At', 'Last Active', ''].map(h => (
                  <th key={h} style={S.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {sessions.map(s => (
                <tr key={s.id}>
                  <td style={S.td}>{s.adminDisplayName || s.adminUserId.slice(0, 8)}</td>
                  <td style={S.td}>{s.deviceInfo}</td>
                  <td style={S.td}>{s.ipAddress}</td>
                  <td style={S.td}>{s.loginAt ? new Date(s.loginAt).toLocaleString() : '—'}</td>
                  <td style={S.td}>{s.lastActiveAt ? new Date(s.lastActiveAt).toLocaleString() : '—'}</td>
                  <td style={S.td}>
                    <button style={S.btn('#E05C6B')} onClick={() => doRevokeSession(s.id)}>
                      <X size={12} /> Revoke
                    </button>
                  </td>
                </tr>
              ))}
              {sessions.length === 0 && (
                <tr><td colSpan={6} style={{ ...S.td, textAlign: 'center', color: '#5C5A6E', padding: 30 }}>No active sessions.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create modal */}
      {showCreate && (
        <Modal title="Create Admin Account" onClose={() => setShowCreate(false)}>
          <label style={S.label}>Display Name</label>
          <input style={S.input} placeholder="e.g. Investor View" value={cDisplayName} onChange={e => setCDisplayName(e.target.value)} />
          <label style={S.label}>Username</label>
          <input style={S.input} placeholder="e.g. investor1" value={cUsername} onChange={e => setCUsername(e.target.value)} autoComplete="off" />
          <label style={S.label}>Password (min 8 chars)</label>
          <input type="password" style={S.input} placeholder="Set a strong password" value={cPassword} onChange={e => setCPassword(e.target.value)} autoComplete="new-password" />
          <label style={{ ...S.label, marginBottom: 10 }}>Permissions</label>
          <PermissionEditor value={cPerms} onChange={setCPerms} />
          <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
            <button
              onClick={doCreate}
              disabled={cLoading || !cUsername || !cPassword || cPassword.length < 8 || !cDisplayName}
              style={{ flex: 1, background: 'linear-gradient(135deg, #9B7FE8, #7B5FC8)', border: 'none', borderRadius: 10, padding: '11px', fontSize: 14, fontWeight: 600, color: '#fff', cursor: 'pointer', opacity: cLoading ? 0.6 : 1 }}
            >
              {cLoading ? 'Creating...' : 'Create Account'}
            </button>
            <button onClick={() => setShowCreate(false)} style={{ padding: '11px 16px', background: '#1C1C28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, color: '#8A8799', cursor: 'pointer', fontSize: 14 }}>
              Cancel
            </button>
          </div>
        </Modal>
      )}

      {/* Edit permissions modal */}
      {editPermsFor && (
        <Modal title={`Permissions — ${editPermsFor.displayName}`} onClose={() => setEditPermsFor(null)}>
          <PermissionEditor value={editedPerms} onChange={setEditedPerms} />
          <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
            <button onClick={doUpdatePerms} style={{ flex: 1, background: 'linear-gradient(135deg, #9B7FE8, #7B5FC8)', border: 'none', borderRadius: 10, padding: '11px', fontSize: 14, fontWeight: 600, color: '#fff', cursor: 'pointer' }}>
              Save Permissions
            </button>
            <button onClick={() => setEditPermsFor(null)} style={{ padding: '11px 16px', background: '#1C1C28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, color: '#8A8799', cursor: 'pointer', fontSize: 14 }}>
              Cancel
            </button>
          </div>
        </Modal>
      )}

      {/* Reset password modal */}
      {resetPwdFor && (
        <Modal title={`Reset Password — ${resetPwdFor.displayName}`} onClose={() => setResetPwdFor(null)}>
          <label style={S.label}>New Password (min 8 chars)</label>
          <input type="password" style={S.input} placeholder="Enter new password" value={newPassword} onChange={e => setNewPassword(e.target.value)} autoComplete="new-password" />
          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button
              onClick={doResetPassword}
              disabled={!newPassword || newPassword.length < 8}
              style={{ flex: 1, background: 'linear-gradient(135deg, #C9A96E, #A0784A)', border: 'none', borderRadius: 10, padding: '11px', fontSize: 14, fontWeight: 600, color: '#fff', cursor: 'pointer', opacity: !newPassword || newPassword.length < 8 ? 0.5 : 1 }}
            >
              Reset Password
            </button>
            <button onClick={() => setResetPwdFor(null)} style={{ padding: '11px 16px', background: '#1C1C28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, color: '#8A8799', cursor: 'pointer', fontSize: 14 }}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
