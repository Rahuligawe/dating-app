import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUsers, deleteUser, createUser, UserSummary } from '../api/adminApi'
import { Search, ChevronRight, ShieldCheck, Trash2, UserPlus, X } from 'lucide-react'

const PLANS = ['ALL', 'FREE', 'PREMIUM', 'ULTRA']

const PLAN_BADGE: Record<string, string> = {
  FREE:    'bg-slate-100 text-slate-600',
  PREMIUM: 'bg-purple-100 text-purple-700',
  ULTRA:   'bg-yellow-100 text-yellow-700',
}

export default function UserManagement() {
  const navigate = useNavigate()
  const [users,   setUsers]   = useState<UserSummary[]>([])
  const [page,    setPage]    = useState(0)
  const [plan,    setPlan]    = useState('ALL')
  const [search,  setSearch]  = useState('')
  const [query,   setQuery]   = useState('')
  const [loading, setLoading] = useState(false)

  // Selection
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [deleting, setDeleting] = useState(false)

  // Add user modal
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
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [page, plan, query])

  useEffect(() => { load() }, [load])

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
    if (selected.size === users.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(users.map(u => u.userId)))
    }
  }

  async function handleDeleteSelected() {
    if (selected.size === 0) return
    if (!confirm(`Delete ${selected.size} user(s)? This will deactivate their accounts.`)) return
    setDeleting(true)
    try {
      await Promise.all([...selected].map(uid => deleteUser(uid)))
      await load()
    } catch (e: any) {
      alert('Delete failed: ' + (e?.response?.data?.error || 'Unknown error'))
    } finally {
      setDeleting(false)
    }
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
    } finally {
      setAddLoading(false)
    }
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">User Management</h1>
        <button onClick={() => setShowAdd(true)}
          className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-pink-500 to-purple-600 text-white rounded-lg text-sm font-medium hover:opacity-90 transition-opacity">
          <UserPlus size={15}/> Add User
        </button>
      </div>

      {/* Filters + bulk actions */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
          {PLANS.map(p => (
            <button key={p} onClick={() => { setPlan(p); setPage(0) }}
              className={`px-4 py-2 text-sm font-medium transition-all ${
                plan === p ? 'bg-gradient-to-r from-pink-500 to-purple-600 text-white' : 'text-slate-500 hover:bg-slate-50'
              }`}>
              {p}
            </button>
          ))}
        </div>

        {selected.size > 0 && (
          <button onClick={handleDeleteSelected} disabled={deleting}
            className="flex items-center gap-2 px-4 py-2 bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors">
            <Trash2 size={14}/>{deleting ? 'Deleting…' : `Delete ${selected.size} selected`}
          </button>
        )}

        <form onSubmit={handleSearch} className="flex gap-2 ml-auto">
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search name or user ID…"
            className="border border-slate-200 rounded-lg px-4 py-2 text-sm w-64 focus:outline-none focus:ring-2 focus:ring-purple-400"/>
          <button type="submit" className="bg-white border border-slate-200 rounded-lg px-3 py-2 hover:bg-slate-50">
            <Search size={16} className="text-slate-500"/>
          </button>
        </form>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-100">
            <tr>
              <th className="px-4 py-3 w-10">
                <input type="checkbox" checked={selected.size === users.length && users.length > 0}
                  onChange={toggleAll}
                  className="rounded border-slate-300 text-purple-600 focus:ring-purple-400"/>
              </th>
              {['User', 'Age / Gender', 'City', 'Plan', 'Joined', ''].map(h => (
                <th key={h} className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {loading && (
              <tr><td colSpan={7} className="text-center py-8 text-slate-400">Loading…</td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={7} className="text-center py-8 text-slate-400">No users found</td></tr>
            )}
            {!loading && users.map(u => (
              <tr key={u.userId}
                className={`hover:bg-slate-50 transition-colors ${selected.has(u.userId) ? 'bg-purple-50' : ''}`}>
                <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                  <input type="checkbox" checked={selected.has(u.userId)}
                    onChange={() => toggleSelect(u.userId)}
                    className="rounded border-slate-300 text-purple-600 focus:ring-purple-400"/>
                </td>
                <td className="px-5 py-3 cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-gradient-to-br from-pink-400 to-purple-600 flex items-center justify-center text-white font-semibold text-sm shrink-0">
                      {u.name?.charAt(0)?.toUpperCase() || '?'}
                    </div>
                    <div>
                      <p className="font-medium text-slate-800 flex items-center gap-1">
                        {u.name || '—'}
                        {u.isVerified && <ShieldCheck size={13} className="text-blue-500"/>}
                      </p>
                      <p className="text-xs text-slate-400 font-mono">{u.userId.slice(0, 8)}…</p>
                    </div>
                  </div>
                </td>
                <td className="px-5 py-3 text-slate-600 cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  {u.age ?? '—'} / {u.gender ?? '—'}
                </td>
                <td className="px-5 py-3 text-slate-600 cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  {u.city || '—'}
                </td>
                <td className="px-5 py-3 cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${PLAN_BADGE[u.subscriptionType] ?? PLAN_BADGE.FREE}`}>
                    {u.subscriptionType}
                  </span>
                </td>
                <td className="px-5 py-3 text-slate-400 text-xs cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  {u.registeredAt ? new Date(u.registeredAt).toLocaleDateString() : '—'}
                </td>
                <td className="px-5 py-3 cursor-pointer" onClick={() => navigate(`/user/${u.userId}`)}>
                  <ChevronRight size={16} className="text-slate-300"/>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="flex items-center justify-between px-5 py-3 border-t border-slate-100 bg-slate-50">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
            className="text-sm text-slate-500 disabled:opacity-30 hover:text-slate-700">← Prev</button>
          <span className="text-xs text-slate-400">Page {page + 1}</span>
          <button disabled={users.length < 20} onClick={() => setPage(p => p + 1)}
            className="text-sm text-slate-500 disabled:opacity-30 hover:text-slate-700">Next →</button>
        </div>
      </div>

      {/* Add User Modal */}
      {showAdd && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setShowAdd(false)}>
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-bold text-slate-800">Add New User</h2>
              <button onClick={() => setShowAdd(false)} className="text-slate-400 hover:text-slate-600"><X size={18}/></button>
            </div>
            <form onSubmit={handleAddUser} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Full Name</label>
                <input value={newName} onChange={e => setNewName(e.target.value)} required
                  placeholder="Enter name" autoFocus
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-400"/>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Mobile Number</label>
                <input value={newMobile} onChange={e => setNewMobile(e.target.value)} required
                  placeholder="+91XXXXXXXXXX"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-400"/>
              </div>
              {addError && <p className="text-xs text-red-500">{addError}</p>}
              <p className="text-xs text-slate-400">The user will appear immediately. They can log in via OTP to activate their account.</p>
              <div className="flex gap-3 pt-1">
                <button type="button" onClick={() => setShowAdd(false)}
                  className="flex-1 px-4 py-2 border border-slate-200 rounded-lg text-sm text-slate-600 hover:bg-slate-50">
                  Cancel
                </button>
                <button type="submit" disabled={addLoading || !newName.trim() || !newMobile.trim()}
                  className="flex-1 px-4 py-2 bg-gradient-to-r from-pink-500 to-purple-600 text-white rounded-lg text-sm font-medium disabled:opacity-50">
                  {addLoading ? 'Creating…' : 'Create User'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
