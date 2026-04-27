interface Props {
  label: string
  value: string | number
  sub?: string
  color?: string   // tailwind bg class for left accent
  icon?: React.ReactNode
}

export default function StatCard({ label, value, sub, color = 'bg-pink-500', icon }: Props) {
  return (
    <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5 flex items-center gap-4">
      {icon && (
        <div className={`${color} w-11 h-11 rounded-xl flex items-center justify-center text-white shrink-0`}>
          {icon}
        </div>
      )}
      <div className="min-w-0">
        <p className="text-xs font-medium text-slate-400 uppercase tracking-wide truncate">{label}</p>
        <p className="text-2xl font-bold text-slate-800 mt-0.5">{value}</p>
        {sub && <p className="text-xs text-slate-500 mt-0.5">{sub}</p>}
      </div>
    </div>
  )
}
