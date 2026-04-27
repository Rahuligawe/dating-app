import { useEffect, useState } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { getRevenue, getAdStats, RevenueStats, AdStats } from '../api/adminApi'
import StatCard from '../components/StatCard'
import { DollarSign, TrendingUp, Eye, Clock } from 'lucide-react'

const REGION_COLORS = ['#7B2FBE', '#E91E8C', '#10B981', '#F59E0B', '#3B82F6']

export default function Revenue() {
  const [revenue, setRevenue] = useState<RevenueStats | null>(null)
  const [ads,     setAds]     = useState<AdStats | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getRevenue(), getAdStats()])
      .then(([r, a]) => { setRevenue(r); setAds(a) })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const regionBarData = (ads?.byRegion ?? []).map((r, i) => ({
    name:        r.region,
    Impressions: r.impressions,
    fill:        REGION_COLORS[i % REGION_COLORS.length],
  }))

  return (
    <div className="p-6 space-y-5">
      <h1 className="text-2xl font-bold text-slate-800">Revenue & Advertising</h1>

      {loading && <p className="text-slate-400 text-sm">Loading…</p>}

      {/* Subscription revenue cards */}
      {revenue && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Monthly Revenue"   value={`₹${revenue.estimatedMonthlyRevenue.toLocaleString()}`} color="bg-purple-600" icon={<DollarSign size={18}/>} />
            <StatCard label="Premium Members"   value={revenue.premiumCount}   sub={`₹${revenue.premiumRevenue.toLocaleString()}/mo`} color="bg-violet-500" icon={<TrendingUp size={18}/>} />
            <StatCard label="Ultra Members"     value={revenue.ultraCount}     sub={`₹${revenue.ultraRevenue.toLocaleString()}/mo`}   color="bg-yellow-500" icon={<TrendingUp size={18}/>} />
            <StatCard label="Total Ad Watch (h)" value={revenue.totalAdWatchHours} sub={`${revenue.avgAdWatchSeconds.toFixed(1)}s avg`} color="bg-teal-500" icon={<Clock size={18}/>} />
          </div>

          {/* Subscription breakdown */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-6">
              <h2 className="font-semibold text-slate-700 mb-4">Subscription Breakdown</h2>
              <div className="space-y-4">
                {[
                  { label: 'Premium',  count: revenue.premiumCount, rev: revenue.premiumRevenue,  color: 'bg-violet-500', price: 299 },
                  { label: 'Ultra',    count: revenue.ultraCount,   rev: revenue.ultraRevenue,    color: 'bg-yellow-500', price: 599 },
                ].map(row => (
                  <div key={row.label} className="flex items-center gap-4">
                    <div className={`w-2 h-10 rounded-full ${row.color}`} />
                    <div className="flex-1">
                      <div className="flex justify-between mb-1">
                        <span className="text-sm font-medium text-slate-700">{row.label} (₹{row.price}/mo)</span>
                        <span className="text-sm font-bold text-slate-800">₹{row.rev.toLocaleString()}</span>
                      </div>
                      <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
                        <div
                          className={`h-full ${row.color} rounded-full`}
                          style={{ width: `${Math.min(100, (row.rev / (revenue.estimatedMonthlyRevenue || 1)) * 100)}%` }}
                        />
                      </div>
                      <p className="text-xs text-slate-400 mt-0.5">{row.count} active members</p>
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-4 pt-4 border-t border-slate-50 flex justify-between">
                <span className="text-sm font-semibold text-slate-700">Total Estimated Monthly</span>
                <span className="text-lg font-bold text-purple-700">₹{revenue.estimatedMonthlyRevenue.toLocaleString()}</span>
              </div>
            </div>

            {/* Ad revenue info */}
            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-6">
              <h2 className="font-semibold text-slate-700 mb-4">Ad Metrics</h2>
              <div className="grid grid-cols-2 gap-4">
                {[
                  { label: 'Total Impressions', value: revenue.totalAdImpressions.toLocaleString(), icon: <Eye size={16}/> },
                  { label: 'Avg Watch Time',    value: `${revenue.avgAdWatchSeconds.toFixed(1)}s`,   icon: <Clock size={16}/> },
                  { label: 'Total Watch (hrs)', value: revenue.totalAdWatchHours,                   icon: <Clock size={16}/> },
                  { label: 'Top Region',        value: revenue.adRegions[0]?.region ?? '—',          icon: <TrendingUp size={16}/> },
                ].map(m => (
                  <div key={m.label} className="bg-slate-50 rounded-lg p-3">
                    <p className="text-xs text-slate-400 flex items-center gap-1">{m.icon}{m.label}</p>
                    <p className="text-xl font-bold text-slate-800 mt-1">{m.value}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}

      {/* Region chart */}
      {regionBarData.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-6">
          <h2 className="font-semibold text-slate-700 mb-4">Ad Impressions by Region</h2>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={regionBarData} barSize={40}>
              <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} />
              <YAxis tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="Impressions" radius={[6, 6, 0, 0]}>
                {regionBarData.map((entry, i) => (
                  <Cell key={i} fill={entry.fill} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {ads && regionBarData.length === 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-8 text-center text-slate-400">
          <Eye size={32} className="mx-auto mb-2 opacity-30" />
          <p>No ad impressions tracked yet.</p>
          <p className="text-xs mt-1">Impressions will appear here once the Android app sends tracking events via <code className="bg-slate-100 px-1 py-0.5 rounded">/api/ads/impression</code></p>
        </div>
      )}
    </div>
  )
}
