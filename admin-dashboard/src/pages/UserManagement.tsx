import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUsers, UserSummary } from '../api/adminApi'
import { Search, ChevronRight, ShieldCheck } from 'lucide-react'

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
  const [query,   setQuery]   = useState('')   // debounced
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getUsers(page, 20, plan, query)
      setUsers(data)
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

  return (
    <div className="p-6 space-y-5">
      <h1 className="text-2xl font-bold text-slate-800">User Management</h1>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        {/* Plan filter */}
        <div className="flex bg-white border border-slate-200 rounded-lg overflow-hidden">
          {PLANS.map(p => (
            <button
              key={p}
              onClick={() => { setPlan(p); setPage(0) }}
              className={`px-4 py-2 text-sm font-medium transition-all ${
                plan === p ? 'bg-gradient-to-r from-pink-500 to-purple-600 text-white' : 'text-slate-500 hover:bg-slate-50'
              }`}
            >
              {p}
            </button>
          ))}
        </div>

        {/* Search */}
        <form onSubmit={handleSearch} className="flex gap-2 ml-auto">
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search name or user ID…"
            className="border border-slate-200 rounded-lg px-4 py-2 text-sm w-64 focus:outline-none focus:ring-2 focus:ring-purple-400"
          />
          <button
            type="submit"
            className="bg-white border border-slate-200 rounded-lg px-3 py-2 hover:bg-slate-50"
          >
            <Search size={16} className="text-slate-500" />
          </button>
        </form>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-100">
            <tr>
              {['User', 'Age / Gender', 'City', 'Plan', 'Joined', ''].map(h => (
                <th key={h} className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50">
            {loading && (
              <tr><td colSpan={6} className="text-center py-8 text-slate-400">Loading…</td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={6} className="text-center py-8 text-slate-400">No users found</td></tr>
            )}
            {!loading && users.map(u => (
              <tr
                key={u.userId}
                onClick={() => navigate(`/user/${u.userId}`)}
                className="hover:bg-slate-50 cursor-pointer transition-colors"
              >
                <td className="px-5 py-3">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-gradient-to-br from-pink-400 to-purple-600 flex items-center justify-center text-white font-semibold text-sm shrink-0">
                      {u.name?.charAt(0)?.toUpperCase() || '?'}
                    </div>
                    <div>
                      <p className="font-medium text-slate-800 flex items-center gap-1">
                        {u.name || '—'}
                        {u.isVerified && <ShieldCheck size={13} className="text-blue-500" />}
                      </p>
                      <p className="text-xs text-slate-400 font-mono">{u.userId.slice(0, 8)}…</p>
                    </div>
                  </div>
                </td>
                <td className="px-5 py-3 text-slate-600">
                  {u.age ?? '—'} / {u.gender ?? '—'}
                </td>
                <td className="px-5 py-3 text-slate-600">{u.city || '—'}</td>
                <td className="px-5 py-3">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${PLAN_BADGE[u.subscriptionType] ?? PLAN_BADGE.FREE}`}>
                    {u.subscriptionType}
                  </span>
                </td>
                <td className="px-5 py-3 text-slate-400 text-xs">
                  {u.registeredAt ? new Date(u.registeredAt).toLocaleDateString() : '—'}
                </td>
                <td className="px-5 py-3">
                  <ChevronRight size={16} className="text-slate-300" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Pagination */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-slate-100 bg-slate-50">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="text-sm text-slate-500 disabled:opacity-30 hover:text-slate-700"
          >
            ← Prev
          </button>
          <span className="text-xs text-slate-400">Page {page + 1}</span>
          <button
            disabled={users.length < 20}
            onClick={() => setPage(p => p + 1)}
            className="text-sm text-slate-500 disabled:opacity-30 hover:text-slate-700"
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  )
}
