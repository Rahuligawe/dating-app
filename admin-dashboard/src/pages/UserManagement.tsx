import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUsers, deleteUser, blockUser, unblockUser, restoreUser, createUser, UserSummary } from '../api/adminApi'
import { Search, ChevronRight, ShieldCheck, Trash2, UserPlus, X, LogOut } from 'lucide-react'

function SlideButton({ label, thumbColor, onClick }: { label: string; thumbColor: string; onClick: () => void }) {
  const [sliding, setSliding] = useState(false)
  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    if (sliding) return
    setSliding(true)
    setTimeout(() => { setSliding(false); onClick() }, 380)
  }
  return (
    <div
      onClick={handleClick}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 0,
        background: 'rgba(255,255,255,0.05)',
        border: '1px solid rgba(255,255,255,0.10)',
        borderRadius: 30, padding: '3px 12px 3px 3px',
        cursor: 'pointer', userSelect: 'none', overflow: 'hidden',
        minWidth: 140, position: 'relative',
      }}
    >
      <div style={{
        width: 26, height: 26, borderRadius: '50%', flexShrink: 0,
        background: thumbColor,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transform: sliding ? 'translateX(110px)' : 'translateX(0)',
        transition: 'transform 0.38s cubic-bezier(0.4,0,0.2,1)',
        boxShadow: '0 1px 6px rgba(0,0,0,0.35)',
        zIndex: 1,
      }}>
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </div>
      <span style={{ fontSize: 11, fontWeight: 600, color: 'rgba(255,255,255,0.65)', paddingLeft: 8, whiteSpace: 'nowrap', letterSpacing: '0.2px' }}>
        {label}
      </span>
    </div>
  )
}

const PLANS = ['ALL', 'FREE', 'PREMIUM', 'ULTRA', 'BLOCKED', 'DELETED']

const TAB_COLOR: Record<string, string> = {
  ALL: '#C9A96E', FREE: '#C9A96E', PREMIUM: '#C9A96E', ULTRA: '#C9A96E',
  BLOCKED: '#E05C6B', DELETED: '#5C5A6E',
}

const S = {
  page:        { background: '#0A0A0F', minHeight: '100vh', padding: '24px 28px', color: '#F0EDE8', fontFamily: "'Outfit', 'Inter', sans-serif" } as React.CSSProperties,
  card:        { background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16 } as React.CSSProperties,
  accent:      '#C9A96E',
  muted:       '#8A8799',
  muted2:      '#5C5A6E',
  border:      'rgba(255,255,255,0.07)',
  red:         '#E05C6B',
  green:       '#4ECFA0',
  blue:        '#5B9EF0',
  violet:      '#9B7FE8',
  text:        '#F0EDE8',
}

const PLAN_BADGE: Record<string, { bg: string; color: string }> = {
  FREE:    { bg: 'rgba(92,90,110,0.15)', color: '#8A8799' },
  PREMIUM: { bg: 'rgba(155,127,232,0.15)', color: '#9B7FE8' },
  ULTRA:   { bg: 'rgba(245,158,11,0.15)', color: '#F59E0B' },
}

export default function UserManagement() {
  const navigate = useNavigate()
  const [users,   setUsers]   = useState<UserSummary[]>([])
  const [page,    setPage]    = useState(0)
  const [plan,    setPlan]    = useState('ALL')
  const [search,  setSearch]  = useState('')
  const [query,   setQuery]   = useState('')
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [deleting,      setDeleting]      = useState(false)
  const [blocking,      setBlocking]      = useState<string | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteNames,   setDeleteNames]   = useState<string[]>([])
  const [showBlockConfirm, setShowBlockConfirm] = useState(false)
  const [blockTarget,   setBlockTarget]   = useState<{ userId: string; name: string; action: 'block' | 'unblock' | 'restore' } | null>(null)
  const [showAdd,    setShowAdd]    = useState(false)
  const [newName,    setNewName]    = useState('')
  const [newMobile,  setNewMobile]  = useState('')
  const [addLoading, setAddLoading] = useState(false)
  const [addError,   setAddError]   = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setSelected(new Set())
    try {
      setUsers(await getUsers(page, 20, plan, query))
    } catch (e) { console.error(e) }
    finally { setLoading(false) }
  }, [page, plan, query])

  useEffect(() => { load() }, [load])

  const handleLogout = () => {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_role')
    navigate('/login')
  }

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setQuery(search)
  }

  function toggleSelect(userId: string) {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(userId) ? next.delete(userId) : next.add(userId)
      return next
    })
  }

  function toggleAll() {
    if (selected.size === users.length && users.length > 0) {
      setSelected(new Set())
    } else {
      setSelected(new Set(users.map(u => u.userId)))
    }
  }

  function handleDeleteSelected() {
    if (selected.size === 0) return
    const names = users.filter(u => selected.has(u.userId)).map(u => u.name || 'Unknown')
    setDeleteNames(names)
    setShowDeleteConfirm(true)
  }

  async function confirmDelete() {
    setShowDeleteConfirm(false)
    setDeleting(true)
    try {
      await Promise.all([...selected].map(uid => deleteUser(uid)))
      await load()
    } catch (e: any) {
      alert('Delete failed: ' + (e?.response?.data?.error || 'Unknown error'))
    } finally { setDeleting(false) }
  }

  function handleBlock(userId: string, name: string, action: 'block' | 'unblock' | 'restore') {
    setBlockTarget({ userId, name, action })
    setShowBlockConfirm(true)
  }

  async function confirmBlock() {
    if (!blockTarget) return
    setShowBlockConfirm(false)
    setBlocking(blockTarget.userId)
    try {
      if (blockTarget.action === 'unblock') await unblockUser(blockTarget.userId)
      else if (blockTarget.action === 'restore') await restoreUser(blockTarget.userId)
      else await blockUser(blockTarget.userId)
      setUsers(prev => prev.filter(u => u.userId !== blockTarget.userId))
    } catch (err: any) {
      alert(err?.response?.data?.error || 'Action failed')
    } finally { setBlocking(null); setBlockTarget(null) }
  }

  async function handleAddUser(e: React.FormEvent) {
    e.preventDefault()
    if (!newName.trim() || !newMobile.trim()) return
    setAddLoading(true)
    setAddError('')
    try {
      const user = await createUser(newName.trim(), newMobile.trim())
      setShowAdd(false)
      setNewName(''); setNewMobile('')
      setUsers(prev => [user, ...prev])
    } catch (e: any) {
      setAddError(e?.response?.data?.error || 'Failed to create user')
    } finally { setAddLoading(false) }
  }

  return (
    <div style={S.page}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Outfit:wght@300;400;500;600&display=swap');
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 4px; height: 4px; }
        ::-webkit-scrollbar-track { background: #0A0A0F; }
        ::-webkit-scrollbar-thumb { background: #2A2A3A; border-radius: 4px; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
        .user-wrap { animation: fadeIn 0.4s ease; }
        .user-row:hover { background: rgba(255,255,255,0.03) !important; }
      `}</style>

      <div className="user-wrap" style={{ maxWidth: 1400, margin: '0 auto' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <div>
            <h1 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 32, fontWeight: 600, color: S.text, margin: 0 }}>User Management</h1>
            <p style={{ fontSize: 13, color: S.muted2, marginTop: 4 }}>Manage and monitor all platform users</p>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button onClick={() => setShowAdd(true)} style={{ background: `linear-gradient(135deg, ${S.accent}, #A0784A)`, border: 'none', borderRadius: 10, padding: '8px 18px', fontSize: 12, fontWeight: 600, color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
              <UserPlus size={14} /> Add User
            </button>
            <button onClick={handleLogout} style={{ background: 'rgba(224,92,107,0.15)', border: `1px solid rgba(224,92,107,0.3)`, borderRadius: 10, padding: '8px 16px', fontSize: 12, color: S.red, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
              <LogOut size={14} /> Logout
            </button>
          </div>
        </div>

        {/* Filters */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', marginBottom: 20 }}>
          <div style={{ display: 'flex', background: '#111118', border: `1px solid ${S.border}`, borderRadius: 12, overflow: 'hidden' }}>
            {PLANS.map(p => {
              const activeColor = TAB_COLOR[p]
              return (
                <button key={p} onClick={() => { setPlan(p); setPage(0) }} style={{ padding: '8px 16px', fontSize: 12, fontWeight: 500, background: plan === p ? activeColor : 'transparent', color: plan === p ? '#fff' : S.muted, border: 'none', cursor: 'pointer', transition: 'all 0.2s' }}>
                  {p}
                </button>
              )
            })}
          </div>

          {selected.size > 0 && (
            <button onClick={handleDeleteSelected} disabled={deleting} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'rgba(224,92,107,0.15)', border: `1px solid rgba(224,92,107,0.3)`, borderRadius: 10, padding: '6px 14px', fontSize: 12, color: S.red, cursor: 'pointer' }}>
              <Trash2 size={12} /> {deleting ? 'Deleting...' : `Delete ${selected.size} selected`}
            </button>
          )}

          <form onSubmit={handleSearch} style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search name or ID..." style={{ background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 10, padding: '8px 14px', fontSize: 13, color: S.text, width: 240, outline: 'none' }} />
            <button type="submit" style={{ background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 10, padding: '8px 12px', cursor: 'pointer' }}>
              <Search size={16} color={S.muted2} />
            </button>
          </form>
        </div>

        {/* Table */}
        <div style={{ ...S.card, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#16161F', borderBottom: `1px solid ${S.border}` }}>
                <th style={{ padding: '14px 16px', width: 40 }}>
                  <input type="checkbox" checked={selected.size === users.length && users.length > 0} onChange={toggleAll} style={{ width: 16, height: 16, cursor: 'pointer' }} />
                </th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>User</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>Age / Gender</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>City</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>Plan</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>Referral Used</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>Joined</th>
                <th style={{ textAlign: 'left', padding: '14px 16px', fontSize: 11, fontWeight: 600, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.6px' }}>Status</th>
                <th style={{ width: 40, padding: '14px 16px' }}></th>
               </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={9} style={{ textAlign: 'center', padding: '48px', color: S.muted2, fontSize: 13 }}>Loading users...</td></tr>
              )}
              {!loading && users.length === 0 && (
                <tr><td colSpan={9} style={{ textAlign: 'center', padding: '48px', color: S.muted2, fontSize: 13 }}>No users found</td></tr>
              )}
              {!loading && users.map(u => {
                const badge = PLAN_BADGE[u.subscriptionType] ?? PLAN_BADGE.FREE
                return (
                  <tr key={u.userId} className="user-row" style={{ borderBottom: `1px solid ${S.border}`, cursor: 'pointer' }} onClick={() => navigate(`/user/${u.userId}`)}>
                    <td style={{ padding: '12px 16px' }} onClick={e => e.stopPropagation()}>
                      <input type="checkbox" checked={selected.has(u.userId)} onChange={() => toggleSelect(u.userId)} style={{ width: 16, height: 16, cursor: 'pointer' }} />
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div style={{ width: 36, height: 36, borderRadius: '50%', background: `linear-gradient(135deg, ${S.accent}, #A0784A)`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 600, color: '#fff' }}>
                          {u.name?.charAt(0)?.toUpperCase() || '?'}
                        </div>
                        <div>
                          <p style={{ fontSize: 14, fontWeight: 500, color: S.text, margin: 0, display: 'flex', alignItems: 'center', gap: 4 }}>
                            {u.name || '—'}
                            {u.isVerified && <ShieldCheck size={12} color={S.blue} />}
                          </p>
                          <p style={{ fontSize: 11, color: S.muted2, fontFamily: 'monospace', marginTop: 2 }}>{u.userId.slice(0, 8)}…</p>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: '12px 16px', fontSize: 13, color: S.muted }}>{u.age ?? '—'} / {u.gender ?? '—'}</td>
                    <td style={{ padding: '12px 16px', fontSize: 13, color: S.muted }}>{u.city || '—'}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ background: badge.bg, color: badge.color, padding: '4px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{u.subscriptionType}</span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      {u.referralUsedCount > 0 ? (
                        <span style={{ background: 'rgba(78,207,160,0.1)', color: S.green, padding: '4px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{u.referralUsedCount}</span>
                      ) : (
                        <span style={{ fontSize: 12, color: S.muted2 }}>—</span>
                      )}
                    </td>
                    <td style={{ padding: '12px 16px', fontSize: 12, color: S.muted2 }}>{u.registeredAt ? new Date(u.registeredAt).toLocaleDateString() : '—'}</td>
                    <td style={{ padding: '12px 16px' }} onClick={e => e.stopPropagation()}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        {plan === 'DELETED' ? (
                          <>
                            <span style={{ fontSize: 11, color: S.muted2, fontWeight: 600 }}>● Deleted</span>
                            {blocking === u.userId
                              ? <span style={{ fontSize: 11, color: S.muted2 }}>...</span>
                              : <SlideButton label="Slide to Restore" thumbColor={S.accent} onClick={() => handleBlock(u.userId, u.name, 'restore')} />
                            }
                          </>
                        ) : plan === 'BLOCKED' || u.isBlocked ? (
                          <>
                            <span style={{ fontSize: 11, color: S.red, fontWeight: 600 }}>● Blocked</span>
                            {blocking === u.userId
                              ? <span style={{ fontSize: 11, color: S.muted2 }}>...</span>
                              : <SlideButton label="Slide to Unblock" thumbColor={S.green} onClick={() => handleBlock(u.userId, u.name, 'unblock')} />
                            }
                          </>
                        ) : (
                          <>
                            <span style={{ fontSize: 11, color: S.green, fontWeight: 600 }}>● Active</span>
                            {blocking === u.userId
                              ? <span style={{ fontSize: 11, color: S.muted2 }}>...</span>
                              : <SlideButton label="Slide to Block" thumbColor={S.red} onClick={() => handleBlock(u.userId, u.name, 'block')} />
                            }
                          </>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <ChevronRight size={16} color={S.muted2} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Pagination */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 20px', borderTop: `1px solid ${S.border}`, background: '#0D0D14' }}>
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={{ background: 'transparent', border: `1px solid ${S.border}`, borderRadius: 8, padding: '6px 14px', fontSize: 12, color: S.muted, cursor: page === 0 ? 'not-allowed' : 'pointer', opacity: page === 0 ? 0.4 : 1 }}>← Prev</button>
            <span style={{ fontSize: 12, color: S.muted2 }}>Page {page + 1}</span>
            <button disabled={users.length < 20} onClick={() => setPage(p => p + 1)} style={{ background: 'transparent', border: `1px solid ${S.border}`, borderRadius: 8, padding: '6px 14px', fontSize: 12, color: S.muted, cursor: users.length < 20 ? 'not-allowed' : 'pointer', opacity: users.length < 20 ? 0.4 : 1 }}>Next →</button>
          </div>
        </div>
      </div>

      {/* Add User Modal */}
      {showAdd && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowAdd(false)}>
          <div style={{ background: '#111118', border: `1px solid ${S.border}`, borderRadius: 20, width: 400, maxWidth: '90%', padding: '24px' }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 24, fontWeight: 600, color: S.text, margin: 0 }}>Add New User</h2>
              <button onClick={() => setShowAdd(false)} style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: S.muted2 }}><X size={18} /></button>
            </div>
            <form onSubmit={handleAddUser}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, color: S.muted2, marginBottom: 4, display: 'block' }}>Full Name</label>
                <input value={newName} onChange={e => setNewName(e.target.value)} required autoFocus style={{ width: '100%', background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 10, padding: '10px 14px', fontSize: 13, color: S.text, outline: 'none' }} />
              </div>
              <div style={{ marginBottom: 16 }}>
                <label style={{ fontSize: 12, color: S.muted2, marginBottom: 4, display: 'block' }}>Mobile Number</label>
                <input value={newMobile} onChange={e => setNewMobile(e.target.value)} required placeholder="+91XXXXXXXXXX" style={{ width: '100%', background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 10, padding: '10px 14px', fontSize: 13, color: S.text, outline: 'none' }} />
              </div>
              {addError && <p style={{ fontSize: 11, color: S.red, marginBottom: 12 }}>{addError}</p>}
              <div style={{ display: 'flex', gap: 12 }}>
                <button type="button" onClick={() => setShowAdd(false)} style={{ flex: 1, background: 'transparent', border: `1px solid ${S.border}`, borderRadius: 10, padding: '10px', fontSize: 13, color: S.muted, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={addLoading || !newName.trim() || !newMobile.trim()} style={{ flex: 1, background: `linear-gradient(135deg, ${S.accent}, #A0784A)`, border: 'none', borderRadius: 10, padding: '10px', fontSize: 13, fontWeight: 600, color: '#fff', cursor: 'pointer', opacity: (addLoading || !newName.trim() || !newMobile.trim()) ? 0.5 : 1 }}>
                  {addLoading ? 'Creating...' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Block Confirmation Modal */}
      {showBlockConfirm && blockTarget && (() => {
        const isUnblock  = blockTarget.action === 'unblock'
        const isRestore  = blockTarget.action === 'restore'
        const borderClr  = isRestore ? 'rgba(201,169,110,0.3)' : isUnblock ? 'rgba(78,207,160,0.3)' : 'rgba(224,92,107,0.3)'
        const btnBg      = isRestore ? 'rgba(201,169,110,0.15)' : isUnblock ? 'rgba(78,207,160,0.15)' : 'rgba(224,92,107,0.15)'
        const btnBorder  = isRestore ? 'rgba(201,169,110,0.4)'  : isUnblock ? 'rgba(78,207,160,0.4)'  : 'rgba(224,92,107,0.4)'
        const btnColor   = isRestore ? S.accent : isUnblock ? S.green : S.red
        const title      = isRestore ? 'Restore User?' : isUnblock ? 'Unblock User?' : 'Block User?'
        const msg        = isRestore
          ? <> Are you sure you want to restore <strong style={{ color: S.text }}>{blockTarget.name}</strong>? Their account will become active again.</>
          : isUnblock
            ? <>Are you sure you want to unblock <strong style={{ color: S.text }}>{blockTarget.name}</strong>? They will be able to log into the app again.</>
            : <>Are you sure you want to block <strong style={{ color: S.text }}>{blockTarget.name}</strong>? They will be suspended and shown an account suspension message.</>
        return (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowBlockConfirm(false)}>
            <div style={{ background: '#111118', border: `1px solid ${borderClr}`, borderRadius: 20, width: 420, maxWidth: '90%', padding: '28px 24px' }} onClick={e => e.stopPropagation()}>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 22, fontWeight: 600, color: S.text, margin: '0 0 14px' }}>{title}</h2>
              <p style={{ fontSize: 13, color: S.muted, marginBottom: 22, lineHeight: 1.7 }}>{msg}</p>
              <div style={{ display: 'flex', gap: 12 }}>
                <button onClick={() => setShowBlockConfirm(false)} style={{ flex: 1, background: 'transparent', border: `1px solid ${S.border}`, borderRadius: 10, padding: '10px', fontSize: 13, color: S.muted, cursor: 'pointer' }}>Cancel</button>
                <button onClick={confirmBlock} style={{ flex: 1, background: btnBg, border: `1px solid ${btnBorder}`, borderRadius: 10, padding: '10px', fontSize: 13, fontWeight: 600, color: btnColor, cursor: 'pointer' }}>
                  Confirm {isRestore ? 'Restore' : isUnblock ? 'Unblock' : 'Block'}
                </button>
              </div>
            </div>
          </div>
        )
      })()}

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowDeleteConfirm(false)}>
          <div style={{ background: '#111118', border: `1px solid rgba(224,92,107,0.3)`, borderRadius: 20, width: 440, maxWidth: '90%', padding: '28px 24px' }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
              <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'rgba(224,92,107,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Trash2 size={16} color={S.red} />
              </div>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 22, fontWeight: 600, color: S.text, margin: 0 }}>Delete {deleteNames.length} user{deleteNames.length > 1 ? 's' : ''}?</h2>
            </div>

            <div style={{ background: '#1C1C28', borderRadius: 10, padding: '12px 14px', marginBottom: 16, maxHeight: 120, overflowY: 'auto' }}>
              {deleteNames.map((name, i) => (
                <p key={i} style={{ fontSize: 13, color: S.text, margin: '2px 0' }}>• {name}</p>
              ))}
            </div>

            <div style={{ background: 'rgba(201,169,110,0.08)', border: `1px solid rgba(201,169,110,0.2)`, borderRadius: 10, padding: '10px 14px', marginBottom: 20 }}>
              <p style={{ fontSize: 12, color: '#C9A96E', margin: 0, lineHeight: 1.6 }}>
                These users will be visible in the <strong>Deleted</strong> tab and can be restored later if required.
              </p>
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button onClick={() => setShowDeleteConfirm(false)} style={{ flex: 1, background: 'transparent', border: `1px solid ${S.border}`, borderRadius: 10, padding: '10px', fontSize: 13, color: S.muted, cursor: 'pointer' }}>Cancel</button>
              <button onClick={confirmDelete} style={{ flex: 1, background: 'rgba(224,92,107,0.15)', border: `1px solid rgba(224,92,107,0.4)`, borderRadius: 10, padding: '10px', fontSize: 13, fontWeight: 600, color: S.red, cursor: 'pointer' }}>
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}