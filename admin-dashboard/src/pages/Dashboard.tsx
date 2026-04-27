import { useEffect, useState, useCallback } from 'react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts'
import { getStats, getGrowth, getRevenue, DashboardStats, UserGrowth, RevenueStats } from '../api/adminApi'
import StatCard from '../components/StatCard'
import { Users, UserCheck, Wifi, TrendingUp, Heart, Zap, Star, DollarSign } from 'lucide-react'

const PLAN_COLORS: Record<string, string> = {
  FREE:    '#94A3B8',
  PREMIUM: '#7B2FBE',
  ULTRA:   '#F59E0B',
}

export default function Dashboard() {
  const [stats,   setStats]   = useState<DashboardStats | null>(null)
  const [growth,  setGrowth]  = useState<UserGrowth | null>(null)
  const [revenue, setRevenue] = useState<RevenueStats | null>(null)
  const [days,    setDays]    = useState(7)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [s, g, r] = await Promise.all([getStats(), getGrowth(days), getRevenue()])
      setStats(s); setGrowth(g); setRevenue(r)
    } catch (e) {
      console.error('Dashboard load failed:', e)
    } finally {
      setLoading(false)
    }
  }, [days])

  useEffect(() => { load() }, [load])

  // Auto-refresh online count every 30s
  useEffect(() => {
    const iv = setInterval(load, 30_000)
    return () => clearInterval(iv)
  }, [load])

  const growthData = growth?.dates.map((d, i) => ({
    date:    d.slice(5),   // MM-DD
    Signups: growth.signups[i],
    Likes:   growth.likes[i],
    Matches: growth.matches[i],
  })) ?? []

  const planData = stats ? [
    { name: 'Free',    value: stats.freeUsers,    color: PLAN_COLORS.FREE    },
    { name: 'Premium', value: stats.premiumUsers, color: PLAN_COLORS.PREMIUM },
    { name: 'Ultra',   value: stats.ultraUsers,   color: PLAN_COLORS.ULTRA   },
  ] : []

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
        <button
          onClick={load}
          className="text-sm bg-white border border-slate-200 rounded-lg px-4 py-2 hover:bg-slate-50 font-medium text-slate-600"
        >
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {/* Top stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Online Users"     value={stats?.onlineUsers ?? '—'}    color="bg-green-500"  icon={<Wifi size={18}/>}      sub="Live right now" />
        <StatCard label="New Today"        value={stats?.newSignupsToday ?? '—'} color="bg-blue-500"   icon={<UserCheck size={18}/>} sub="Signups" />
        <StatCard label="Total Users"      value={stats?.totalUsers ?? '—'}     color="bg-purple-600" icon={<Users size={18}/>} />
        <StatCard label="Monthly Revenue"  value={revenue ? `₹${revenue.estimatedMonthlyRevenue.toLocaleString()}` : '—'} color="bg-amber-500" icon={<DollarSign size={18}/>} />
      </div>

      {/* Second row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Premium Members" value={stats?.premiumUsers ?? '—'} color="bg-violet-600" icon={<Star size={18}/>} />
        <StatCard label="Ultra Members"   value={stats?.ultraUsers ?? '—'}   color="bg-yellow-500" icon={<Zap size={18}/>}  />
        <StatCard label="Total Matches"   value={stats?.totalMatches ?? '—'} color="bg-pink-500"   icon={<Heart size={18}/>}/>
        <StatCard label="Swipes Today"    value={stats?.swipesToday ?? '—'}  color="bg-rose-500"   icon={<TrendingUp size={18}/>} />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

        {/* Growth chart (2/3 width) */}
        <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-slate-100 p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-slate-700">User Overview</h2>
            <select
              value={days}
              onChange={e => setDays(Number(e.target.value))}
              className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none"
            >
              <option value={7}>Last 7 Days</option>
              <option value={14}>Last 14 Days</option>
              <option value={30}>Last 30 Days</option>
            </select>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={growthData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="Signups" stroke="#7B2FBE" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="Likes"   stroke="#10B981" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="Matches" stroke="#E91E8C" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Plan distribution pie */}
        <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
          <h2 className="font-semibold text-slate-700 mb-4">Membership Split</h2>
          <ResponsiveContainer width="100%" height={220}>
            <PieChart>
              <Pie data={planData} cx="50%" cy="50%" innerRadius={55} outerRadius={85} dataKey="value" label={({ name, percent }) => `${name} ${(percent*100).toFixed(0)}%`}>
                {planData.map((entry, i) => <Cell key={i} fill={entry.color} />)}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
          <div className="flex justify-center gap-4 mt-2">
            {planData.map(p => (
              <div key={p.name} className="flex items-center gap-1.5 text-xs text-slate-600">
                <span className="w-3 h-3 rounded-full inline-block" style={{ background: p.color }} />
                {p.name}: <strong>{p.value?.toLocaleString()}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Ad stats row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Total Ad Impressions" value={stats?.totalAdImpressions?.toLocaleString() ?? '—'} color="bg-teal-500"  />
        <StatCard label="Impressions Today"    value={stats?.adImpressionsToday ?? '—'}                   color="bg-cyan-500"  />
        <StatCard label="Total Watch (hrs)"    value={revenue?.totalAdWatchHours ?? '—'}                  color="bg-sky-500"   />
        <StatCard label="Avg Ad Watch"         value={revenue ? `${revenue.avgAdWatchSeconds.toFixed(1)}s` : '—'} color="bg-indigo-500" />
      </div>

      {/* Revenue & Region */}
      {revenue && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
            <h2 className="font-semibold text-slate-700 mb-4">Membership Revenue</h2>
            <div className="space-y-3">
              <div className="flex justify-between items-center py-2 border-b border-slate-50">
                <span className="text-sm text-slate-600">Premium ({revenue.premiumCount} members)</span>
                <span className="font-semibold text-violet-600">₹{revenue.premiumRevenue.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-center py-2 border-b border-slate-50">
                <span className="text-sm text-slate-600">Ultra ({revenue.ultraCount} members)</span>
                <span className="font-semibold text-yellow-600">₹{revenue.ultraRevenue.toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-center py-2 bg-purple-50 rounded-lg px-3">
                <span className="text-sm font-semibold text-slate-700">Total Est. Monthly</span>
                <span className="font-bold text-purple-700 text-lg">₹{revenue.estimatedMonthlyRevenue.toLocaleString()}</span>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
            <h2 className="font-semibold text-slate-700 mb-4">Ad Impressions by Region</h2>
            <div className="space-y-2">
              {revenue.adRegions.length === 0 && (
                <p className="text-sm text-slate-400">No impression data yet</p>
              )}
              {revenue.adRegions.map(r => (
                <div key={r.region} className="flex items-center gap-3">
                  <span className="text-sm font-medium text-slate-600 w-10">{r.region}</span>
                  <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-pink-500 to-purple-600 rounded-full"
                      style={{ width: `${Math.min(100, (r.impressions / (revenue.totalAdImpressions || 1)) * 100)}%` }}
                    />
                  </div>
                  <span className="text-sm text-slate-500 w-16 text-right">{r.impressions.toLocaleString()}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
