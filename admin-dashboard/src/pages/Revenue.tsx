import { useEffect, useState } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { getRevenue, getAdStats, RevenueStats, AdStats } from '../api/adminApi'
import { DollarSign, TrendingUp, Eye, Clock, LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const REGION_COLORS = ['#9B7FE8', '#E06B9A', '#4ECFA0', '#F59E0B', '#5B9EF0', '#C9A96E']

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

export default function Revenue() {
  const navigate = useNavigate()
  const [revenue, setRevenue] = useState<RevenueStats | null>(null)
  const [ads,     setAds]     = useState<AdStats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getRevenue(), getAdStats()])
      .then(([r, a]) => { setRevenue(r); setAds(a) })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const handleLogout = () => {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_role')
    navigate('/login')
  }

  const regionBarData = (ads?.byRegion ?? revenue?.adRegions ?? []).map((r, i) => ({
    name:        r.region,
    Impressions: r.impressions,
    fill:        REGION_COLORS[i % REGION_COLORS.length],
  }))

  return (
    <div style={S.page}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Outfit:wght@300;400;500;600&display=swap');
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 4px; height: 4px; }
        ::-webkit-scrollbar-track { background: #0A0A0F; }
        ::-webkit-scrollbar-thumb { background: #2A2A3A; border-radius: 4px; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
        .revenue-wrap { animation: fadeIn 0.4s ease; }
      `}</style>

      <div className="revenue-wrap" style={{ maxWidth: 1400, margin: '0 auto' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <div>
            <h1 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 32, fontWeight: 600, color: S.text, margin: 0 }}>Revenue & Advertising</h1>
            <p style={{ fontSize: 13, color: S.muted2, marginTop: 4 }}>Monetization and ad performance insights</p>
          </div>
          <button onClick={handleLogout} style={{ background: 'rgba(224,92,107,0.15)', border: `1px solid rgba(224,92,107,0.3)`, borderRadius: 10, padding: '8px 16px', fontSize: 12, color: S.red, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
            <LogOut size={14} /> Logout
          </button>
        </div>

        {loading && <p style={{ textAlign: 'center', color: S.muted2, padding: '48px' }}>Loading...</p>}

        {!loading && revenue && (
          <>
            {/* Stat Cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
              <StatCardDark label="Monthly Revenue" value={`₹${revenue.estimatedMonthlyRevenue.toLocaleString()}`} icon={<DollarSign size={16} />} color={S.accent} />
              <StatCardDark label="Premium Members" value={revenue.premiumCount} sub={`₹${revenue.premiumRevenue.toLocaleString()}/mo`} icon={<TrendingUp size={16} />} color={S.violet} />
              <StatCardDark label="Ultra Members" value={revenue.ultraCount} sub={`₹${revenue.ultraRevenue.toLocaleString()}/mo`} icon={<TrendingUp size={16} />} color="#F59E0B" />
              <StatCardDark label="Ad Watch Hours" value={revenue.totalAdWatchHours} sub={`${revenue.avgAdWatchSeconds.toFixed(1)}s avg`} icon={<Clock size={16} />} color={S.blue} />
            </div>

            {/* Revenue Breakdown & Ad Metrics */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 24 }}>
              <div style={{ ...S.card, padding: '20px 24px' }}>
                <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 20 }}>Subscription Breakdown</h2>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid ${S.border}` }}>
                    <span style={{ fontSize: 13, color: S.muted }}>Premium (₹299/mo) · {revenue.premiumCount} members</span>
                    <span style={{ fontSize: 15, fontWeight: 600, color: S.violet }}>₹{revenue.premiumRevenue.toLocaleString()}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid ${S.border}` }}>
                    <span style={{ fontSize: 13, color: S.muted }}>Ultra (₹599/mo) · {revenue.ultraCount} members</span>
                    <span style={{ fontSize: 15, fontWeight: 600, color: '#F59E0B' }}>₹{revenue.ultraRevenue.toLocaleString()}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '14px 12px', marginTop: 12, background: S.accentFaint, borderRadius: 12 }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: S.text }}>Total Monthly Revenue</span>
                    <span style={{ fontSize: 20, fontWeight: 700, color: S.accent }}>₹{revenue.estimatedMonthlyRevenue.toLocaleString()}</span>
                  </div>
                </div>
              </div>

              <div style={{ ...S.card, padding: '20px 24px' }}>
                <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 20 }}>Ad Metrics</h2>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div style={{ background: '#1C1C28', borderRadius: 12, padding: '12px' }}>
                    <p style={{ fontSize: 11, color: S.muted2, display: 'flex', alignItems: 'center', gap: 4 }}><Eye size={12} /> Total Impressions</p>
                    <p style={{ fontSize: 22, fontWeight: 700, color: S.text }}>{revenue.totalAdImpressions.toLocaleString()}</p>
                  </div>
                  <div style={{ background: '#1C1C28', borderRadius: 12, padding: '12px' }}>
                    <p style={{ fontSize: 11, color: S.muted2, display: 'flex', alignItems: 'center', gap: 4 }}><Clock size={12} /> Avg Watch Time</p>
                    <p style={{ fontSize: 22, fontWeight: 700, color: S.text }}>{revenue.avgAdWatchSeconds.toFixed(1)}s</p>
                  </div>
                  <div style={{ background: '#1C1C28', borderRadius: 12, padding: '12px' }}>
                    <p style={{ fontSize: 11, color: S.muted2, display: 'flex', alignItems: 'center', gap: 4 }}><Clock size={12} /> Total Watch Hours</p>
                    <p style={{ fontSize: 22, fontWeight: 700, color: S.text }}>{revenue.totalAdWatchHours}</p>
                  </div>
                  <div style={{ background: '#1C1C28', borderRadius: 12, padding: '12px' }}>
                    <p style={{ fontSize: 11, color: S.muted2, display: 'flex', alignItems: 'center', gap: 4 }}><TrendingUp size={12} /> Top Region</p>
                    <p style={{ fontSize: 18, fontWeight: 600, color: S.accent }}>{revenue.adRegions[0]?.region ?? '—'}</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Region Chart */}
            {regionBarData.length > 0 && (
              <div style={{ ...S.card, padding: '20px 24px' }}>
                <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 20, fontWeight: 600, color: S.text, marginBottom: 20 }}>Ad Impressions by Region</h2>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={regionBarData} barSize={50}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="name" tick={{ fontSize: 11, fill: S.muted2 }} />
                    <YAxis tick={{ fontSize: 11, fill: S.muted2 }} />
                    <Tooltip contentStyle={{ background: '#16161F', border: `1px solid ${S.border}`, borderRadius: 8 }} labelStyle={{ color: S.muted2 }} />
                    <Bar dataKey="Impressions" radius={[8, 8, 0, 0]}>
                      {regionBarData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}

            {regionBarData.length === 0 && (
              <div style={{ ...S.card, padding: '48px', textAlign: 'center' }}>
                <Eye size={40} color={S.muted2} style={{ opacity: 0.3, marginBottom: 12 }} />
                <p style={{ fontSize: 13, color: S.muted2 }}>No ad impressions tracked yet.</p>
                <p style={{ fontSize: 11, color: S.muted2, marginTop: 4 }}>Impressions will appear once the Android app sends tracking events.</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function StatCardDark({ label, value, icon, sub, color }: { label: string; value: string | number; icon: React.ReactNode; sub?: string; color?: string }) {
  return (
    <div style={{ background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '16px 18px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 12, fontWeight: 500, color: '#5C5A6E', textTransform: 'uppercase', letterSpacing: '0.6px' }}>{label}</span>
        <span style={{ color: color || '#C9A96E', opacity: 0.7 }}>{icon}</span>
      </div>
      <p style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 28, fontWeight: 600, color: '#F0EDE8', margin: 0 }}>{value}</p>
      {sub && <p style={{ fontSize: 11, color: '#5C5A6E', marginTop: 6 }}>{sub}</p>}
    </div>
  )
}