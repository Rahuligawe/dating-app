import { useEffect, useState, useCallback } from 'react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts'
import { getStats, getGrowth, getRevenue, DashboardStats, UserGrowth, RevenueStats } from '../api/adminApi'
import { Users, UserCheck, Wifi, TrendingUp, Heart, Zap, Star, DollarSign, LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const PLAN_COLORS: Record<string, string> = {
  FREE:    '#5C5A6E',
  PREMIUM: '#9B7FE8',
  ULTRA:   '#F59E0B',
}

const S = {
  page:        { background: '#0A0A0F', minHeight: '100vh', padding: '24px 28px', color: '#F0EDE8', fontFamily: "'Outfit', 'Inter', sans-serif" } as React.CSSProperties,
  card:        { background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16 } as React.CSSProperties,
  cardInner:   { background: '#16161F', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12 } as React.CSSProperties,
  accent:      '#C9A96E',
  accentFaint: 'rgba(201,169,110,0.12)',
  muted:       '#8A8799',
  muted2:      '#5C5A6E',
  border:      'rgba(255,255,255,0.07)',
  red:         '#E05C6B',
  green:       '#4ECFA0',
  blue:        '#5B9EF0',
  violet:      '#9B7FE8',
  text:        '#F0EDE8',
}

export default function Dashboard() {
  const navigate = useNavigate()
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

  useEffect(() => {
    const iv = setInterval(load, 30000)
    return () => clearInterval(iv)
  }, [load])

  const growthData = growth?.dates.map((d, i) => ({
    date:    d.slice(5),
    Signups: growth.signups[i],
    Likes:   growth.likes[i],
    Matches: growth.matches[i],
  })) ?? []

  const planData = stats ? [
    { name: 'Free',    value: stats.freeUsers,    color: PLAN_COLORS.FREE    },
    { name: 'Premium', value: stats.premiumUsers, color: PLAN_COLORS.PREMIUM },
    { name: 'Ultra',   value: stats.ultraUsers,   color: PLAN_COLORS.ULTRA   },
  ] : []

  const handleLogout = () => {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_role')
    navigate('/login')
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
        .dashboard-wrap { animation: fadeIn 0.4s ease; }
      `}</style>

      <div className="dashboard-wrap" style={{ maxWidth: 1400, margin: '0 auto' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <div>
            <h1 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 32, fontWeight: 600, color: S.text, margin: 0 }}>Dashboard</h1>
            <p style={{ fontSize: 13, color: S.muted2, marginTop: 4 }}>Real-time platform insights</p>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button onClick={load} style={{ background: S.cardInner.background, border: `1px solid ${S.border}`, borderRadius: 10, padding: '8px 16px', fontSize: 12, color: S.muted, cursor: 'pointer', fontFamily: 'inherit' }}>
              {loading ? 'Refreshing...' : '↻ Refresh'}
            </button>
            <button onClick={handleLogout} style={{ background: 'rgba(224,92,107,0.15)', border: `1px solid rgba(224,92,107,0.3)`, borderRadius: 10, padding: '8px 16px', fontSize: 12, color: S.red, cursor: 'pointer', fontFamily: 'inherit', display: 'flex', alignItems: 'center', gap: 6 }}>
              <LogOut size={14} /> Logout
            </button>
          </div>
        </div>

        {/* Top stat cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 20 }}>
          <StatCardDark label="Online Users" value={stats?.onlineUsers ?? '—'} icon={<Wifi size={16} />} sub="Live now" color={S.green} />
          <StatCardDark label="New Today" value={stats?.newSignupsToday ?? '—'} icon={<UserCheck size={16} />} sub="Signups" color={S.blue} />
          <StatCardDark label="Total Users" value={stats?.totalUsers ?? '—'} icon={<Users size={16} />} color={S.violet} />
          <StatCardDark label="Monthly Revenue" value={revenue ? `₹${revenue.estimatedMonthlyRevenue.toLocaleString()}` : '—'} icon={<DollarSign size={16} />} color={S.accent} />
        </div>

        {/* Second row */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
          <StatCardDark label="Premium" value={stats?.premiumUsers ?? '—'} icon={<Star size={16} />} color={S.violet} />
          <StatCardDark label="Ultra" value={stats?.ultraUsers ?? '—'} icon={<Zap size={16} />} color="#F59E0B" />
          <StatCardDark label="Total Matches" value={stats?.totalMatches ?? '—'} icon={<Heart size={16} />} color="#E06B9A" />
          <StatCardDark label="Swipes Today" value={stats?.swipesToday ?? '—'} icon={<TrendingUp size={16} />} color={S.red} />
        </div>

        {/* Charts row */}
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20, marginBottom: 20 }}>
          {/* Growth chart */}
          <div style={{ ...S.card, padding: '20px 24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, margin: 0 }}>User Activity</h2>
              <select value={days} onChange={e => setDays(Number(e.target.value))} style={{ background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 8, padding: '6px 12px', fontSize: 12, color: S.muted, fontFamily: 'inherit' }}>
                <option value={7}>Last 7 days</option>
                <option value={14}>Last 14 days</option>
                <option value={30}>Last 30 days</option>
              </select>
            </div>
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={growthData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: S.muted2 }} />
                <YAxis tick={{ fontSize: 11, fill: S.muted2 }} />
                <Tooltip contentStyle={{ background: '#16161F', border: `1px solid ${S.border}`, borderRadius: 8 }} labelStyle={{ color: S.muted2 }} />
                <Legend wrapperStyle={{ color: S.muted2 }} />
                <Line type="monotone" dataKey="Signups" stroke={S.blue} strokeWidth={2} dot={{ fill: S.blue, r: 3 }} />
                <Line type="monotone" dataKey="Likes" stroke={S.green} strokeWidth={2} dot={{ fill: S.green, r: 3 }} />
                <Line type="monotone" dataKey="Matches" stroke="#E06B9A" strokeWidth={2} dot={{ fill: '#E06B9A', r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Pie chart */}
          <div style={{ ...S.card, padding: '20px 24px' }}>
            <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 20 }}>Membership Split</h2>
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={planData} cx="50%" cy="50%" innerRadius={50} outerRadius={80} dataKey="value" label={({ name, percent }) => `${name} ${(percent*100).toFixed(0)}%`} labelLine={false}>
                  {planData.map((entry, i) => <Cell key={i} fill={entry.color} />)}
                </Pie>
                <Tooltip contentStyle={{ background: '#16161F', border: `1px solid ${S.border}`, borderRadius: 8 }} />
              </PieChart>
            </ResponsiveContainer>
            <div style={{ display: 'flex', justifyContent: 'center', gap: 20, marginTop: 16 }}>
              {planData.map(p => (
                <div key={p.name} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ width: 10, height: 10, borderRadius: '50%', background: p.color, display: 'inline-block' }} />
                  <span style={{ fontSize: 12, color: S.muted }}>{p.name}: <strong style={{ color: S.text }}>{p.value?.toLocaleString()}</strong></span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Bottom stats */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 20 }}>
          <StatCardDark label="Ad Impressions" value={stats?.totalAdImpressions?.toLocaleString() ?? '—'} icon={<Eye size={16} />} color={S.blue} />
          <StatCardDark label="Impressions Today" value={stats?.adImpressionsToday ?? '—'} icon={<Eye size={16} />} color={S.green} />
          <StatCardDark label="Watch Hours" value={revenue?.totalAdWatchHours ?? '—'} icon={<Clock size={16} />} color={S.accent} />
          <StatCardDark label="Avg Ad Watch" value={revenue ? `${revenue.avgAdWatchSeconds.toFixed(1)}s` : '—'} icon={<Clock size={16} />} color={S.violet} />
        </div>

        {/* Revenue breakdown */}
        {revenue && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
            <div style={{ ...S.card, padding: '20px 24px' }}>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 16 }}>Membership Revenue</h2>
              <div style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, color: S.muted }}>Premium ({revenue.premiumCount} members)</span>
                  <span style={{ fontSize: 15, fontWeight: 600, color: S.violet }}>₹{revenue.premiumRevenue.toLocaleString()}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, color: S.muted }}>Ultra ({revenue.ultraCount} members)</span>
                  <span style={{ fontSize: 15, fontWeight: 600, color: '#F59E0B' }}>₹{revenue.ultraRevenue.toLocaleString()}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', marginTop: 8, background: S.accentFaint, borderRadius: 10, paddingLeft: 12, paddingRight: 12 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: S.text }}>Total Monthly</span>
                  <span style={{ fontSize: 18, fontWeight: 700, color: S.accent }}>₹{revenue.estimatedMonthlyRevenue.toLocaleString()}</span>
                </div>
              </div>
            </div>

            <div style={{ ...S.card, padding: '20px 24px' }}>
              <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 16 }}>Ad Regions</h2>
              {revenue.adRegions.length === 0 ? (
                <p style={{ fontSize: 13, color: S.muted2, textAlign: 'center', padding: '20px 0' }}>No impression data yet</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {revenue.adRegions.map(r => (
                    <div key={r.region}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                        <span style={{ fontSize: 12, color: S.muted }}>{r.region}</span>
                        <span style={{ fontSize: 12, color: S.text }}>{r.impressions.toLocaleString()}</span>
                      </div>
                      <div style={{ height: 4, background: '#1C1C28', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${Math.min(100, (r.impressions / (revenue.totalAdImpressions || 1)) * 100)}%`, height: '100%', background: `linear-gradient(90deg, ${S.accent}, ${S.violet})`, borderRadius: 4 }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// Dark Stat Card Component
function StatCardDark({ label, value, icon, sub, color }: { label: string; value: string | number; icon: React.ReactNode; sub?: string; color?: string }) {
  return (
    <div style={{ background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '16px 18px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 12, fontWeight: 500, color: '#5C5A6E', textTransform: 'uppercase', letterSpacing: '0.6px' }}>{label}</span>
        <span style={{ color: color || '#C9A96E', opacity: 0.7 }}>{icon}</span>
      </div>
      <p style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 32, fontWeight: 600, color: '#F0EDE8', margin: 0 }}>{value}</p>
      {sub && <p style={{ fontSize: 11, color: '#5C5A6E', marginTop: 6 }}>{sub}</p>}
    </div>
  )
}

// Missing imports
import { Eye, Clock } from 'lucide-react'