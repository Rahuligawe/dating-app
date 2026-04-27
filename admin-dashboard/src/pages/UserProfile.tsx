import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  getUserDetail, getUserPosts, getUserSwipes, getUserPostInteractions,
  getUserMatches, getUserChats, getUserPostEngagements, giveRewardPoints,
  getChatMessages, removePoints, getUserByReferralCode, giftPoints,
  getDailyStats, getWhoSwipedUser, getSessionStats, createChatStream,
  UserDetail, MoodPost, SwipeActivity, PostInteraction, MatchInfo,
  ChatSummary, PostEngagement, ChatMessage, DailyStat, StatUser, SessionDailyStat,
} from '../api/adminApi'
import {
  ArrowLeft, Heart, HeartCrack, Zap, Users, FileText,
  ShieldCheck, Image, Coins, Send, MessageCircle,
  ChevronDown, ChevronUp, X, TrendingUp, Gift, Minus,
} from 'lucide-react'

const SORT_OPTIONS  = ['date', 'likes', 'dislikes', 'comments']
const SWIPE_ACTIONS = ['ALL', 'LIKE', 'DISLIKE', 'SUPER_LIKE']

const ACTION_COLOR: Record<string, string> = {
  LIKE:       'bg-green-100 text-green-700',
  DISLIKE:    'bg-red-100 text-red-600',
  SUPER_LIKE: 'bg-purple-100 text-purple-700',
  COMMENT:    'bg-blue-100 text-blue-700',
}

type Tab = 'posts' | 'swipes' | 'interactions' | 'engagements' | 'matches' | 'chats'
type RightPanel = null | 'liked-by' | 'disliked-by' | 'super-liked-by' | 'all-swiped'

export default function UserProfile() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [detail,       setDetail]       = useState<UserDetail | null>(null)
  const [posts,        setPosts]        = useState<MoodPost[]>([])
  const [swipes,       setSwipes]       = useState<SwipeActivity[]>([])
  const [interactions, setInteractions] = useState<PostInteraction[]>([])
  const [engagements,  setEngagements]  = useState<PostEngagement[]>([])
  const [matches,      setMatches]      = useState<MatchInfo[]>([])
  const [chats,        setChats]        = useState<ChatSummary[]>([])
  const [dailyStats,    setDailyStats]    = useState<DailyStat[]>([])
  const [sessionStats,  setSessionStats]  = useState<SessionDailyStat[]>([])
  const [tab,           setTab]           = useState<Tab>('posts')
  const [postSort,     setPostSort]     = useState('date')
  const [swipeAction,  setSwipeAction]  = useState('ALL')
  const [selectedPhoto, setSelectedPhoto] = useState(0)
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState('')

  // Right panel (who swiped this user)
  const [rightPanel,      setRightPanel]      = useState<RightPanel>(null)
  const [rightPanelUsers, setRightPanelUsers] = useState<StatUser[]>([])
  const [rightPanelLoading, setRightPanelLoading] = useState(false)

  // Chat expand state
  const [expandedChat,   setExpandedChat]   = useState<string | null>(null)
  const [chatMessages,   setChatMessages]   = useState<Record<string, ChatMessage[]>>({})
  const [chatMsgLoading, setChatMsgLoading] = useState<string | null>(null)

  // Reward points
  const [rewardAmount,  setRewardAmount]  = useState('')
  const [rewardReason,  setRewardReason]  = useState('')
  const [rewardLoading, setRewardLoading] = useState(false)
  const [rewardMsg,     setRewardMsg]     = useState('')
  const [currentBalance, setCurrentBalance] = useState(0)
  const [removeMode,    setRemoveMode]    = useState(false)

  // Gift points
  const [giftCode,      setGiftCode]      = useState('')
  const [giftAmount,    setGiftAmount]    = useState('')
  const [giftReason,    setGiftReason]    = useState('')
  const [giftRecipient, setGiftRecipient] = useState<{name:string;userId:string}|null>(null)
  const [giftLookupLoading, setGiftLookupLoading] = useState(false)
  const [giftLoading,   setGiftLoading]   = useState(false)
  const [giftMsg,       setGiftMsg]       = useState('')
  const giftCodeTimer = useRef<ReturnType<typeof setTimeout>|null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    Promise.all([
      getUserDetail(id),
      getUserPosts(id, postSort),
      getUserSwipes(id, swipeAction),
      getUserPostInteractions(id),
      getUserPostEngagements(id),
      getUserMatches(id),
      getUserChats(id),
      getDailyStats(id, 7),
      getSessionStats(id, 7),
    ]).then(([d, p, s, pi, pe, m, c, ds, ss]) => {
      setDetail(d); setCurrentBalance(d.pointsBalance ?? 0)
      setPosts(p); setSwipes(s); setInteractions(pi)
      setEngagements(pe); setMatches(m); setChats(c)
      setDailyStats(ds); setSessionStats(ss)
    }).catch(err => setError(err?.response?.data?.error || 'User not found'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => { if (id) getUserPosts(id, postSort).then(setPosts).catch(console.error) }, [id, postSort])
  useEffect(() => { if (id) getUserSwipes(id, swipeAction).then(setSwipes).catch(console.error) }, [id, swipeAction])

  // Right panel loader
  const openRightPanel = async (type: RightPanel) => {
    if (!id || type === null) { setRightPanel(null); return }
    setRightPanel(type)
    setRightPanelLoading(true)
    try {
      const action = type === 'liked-by' ? 'LIKE' : type === 'disliked-by' ? 'DISLIKE' : type === 'super-liked-by' ? 'SUPER_LIKE' : 'ALL'
      setRightPanelUsers(await getWhoSwipedUser(id, action))
    } catch { setRightPanelUsers([]) }
    finally { setRightPanelLoading(false) }
  }

  // Chat expand + real-time SSE (triggered by Redis pub/sub, zero latency)
  const sseCleanupRef = useRef<(() => void) | null>(null)

  const handleExpandChat = async (conversationId: string) => {
    if (expandedChat === conversationId) {
      setExpandedChat(null)
      sseCleanupRef.current?.(); sseCleanupRef.current = null
      return
    }
    // Stop previous SSE if switching conversations
    sseCleanupRef.current?.(); sseCleanupRef.current = null
    setExpandedChat(conversationId)

    // Load historical messages first
    setChatMsgLoading(conversationId)
    try {
      const msgs = await getChatMessages(conversationId)
      setChatMessages(prev => ({ ...prev, [conversationId]: msgs }))
    } catch { /* ignore */ }
    setChatMsgLoading(null)

    // Connect SSE — fires immediately when chat-service sends a message via Redis
    sseCleanupRef.current = createChatStream(conversationId, (incoming) => {
      setChatMessages(prev => {
        const existing = prev[conversationId] ?? []
        const id = incoming['id'] as string | undefined
        if (id && existing.some((m: ChatMessage) => m.id === id)) return prev
        // Prepend (list is stored newest-first to match DESC backend order)
        return { ...prev, [conversationId]: [incoming as unknown as ChatMessage, ...existing] }
      })
    })
  }
  useEffect(() => () => { sseCleanupRef.current?.() }, [])

  // Gift code live lookup (debounced)
  const handleGiftCodeChange = (code: string) => {
    setGiftCode(code)
    setGiftRecipient(null)
    if (giftCodeTimer.current) clearTimeout(giftCodeTimer.current)
    if (code.length < 4) return
    giftCodeTimer.current = setTimeout(async () => {
      setGiftLookupLoading(true)
      try {
        const r = await getUserByReferralCode(code)
        if (r.found) setGiftRecipient({ name: r.name, userId: r.userId })
        else setGiftRecipient(null)
      } catch { setGiftRecipient(null) }
      finally { setGiftLookupLoading(false) }
    }, 600)
  }

  const handlePoints = async () => {
    if (!id || !rewardAmount || Number(rewardAmount) <= 0) return
    setRewardLoading(true); setRewardMsg('')
    try {
      const fn = removeMode ? removePoints : giveRewardPoints
      const res = await fn(id, Number(rewardAmount), rewardReason || (removeMode ? 'Admin deduction' : 'Admin reward'))
      setCurrentBalance(res.newBalance)
      setRewardMsg(`✓ ${removeMode ? 'Removed' : 'Credited'} ${Number(rewardAmount)} pts. New balance: ${res.newBalance}`)
      setRewardAmount(''); setRewardReason('')
    } catch (e: any) {
      setRewardMsg('✗ ' + (e?.response?.data?.error || 'Failed'))
    } finally { setRewardLoading(false) }
  }

  const handleGiftPoints = async () => {
    if (!id || !giftRecipient || !giftAmount || Number(giftAmount) <= 0) return
    setGiftLoading(true); setGiftMsg('')
    try {
      const res = await giftPoints(id, giftCode, Number(giftAmount), giftReason || 'Gift points')
      setCurrentBalance(res.senderNewBalance)
      setGiftMsg(`✓ ${res.gifted} pts gifted to ${res.toUserName}! Your new balance: ${res.senderNewBalance}`)
      setGiftCode(''); setGiftAmount(''); setGiftReason(''); setGiftRecipient(null)
    } catch (e: any) {
      setGiftMsg('✗ ' + (e?.response?.data?.error || 'Gift failed'))
    } finally { setGiftLoading(false) }
  }

  if (loading) return <div className="flex items-center justify-center h-64 text-slate-400">Loading profile…</div>
  if (error || !detail) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-slate-500">{error || 'User not found'}</p>
      <button onClick={() => navigate(-1)} className="text-sm text-purple-600 hover:underline">← Back</button>
    </div>
  )

  const photos = detail.photos ?? []
  const TABS: { key: Tab; label: string; count: number }[] = [
    { key: 'posts',        label: 'Mood Posts',         count: posts.length },
    { key: 'swipes',       label: 'Swipe Activity',     count: swipes.length },
    { key: 'interactions', label: 'Interactions Given', count: interactions.length },
    { key: 'engagements',  label: 'Post Engagements',   count: engagements.length },
    { key: 'matches',      label: 'Matches',            count: matches.length },
    { key: 'chats',        label: 'Chats',              count: chats.length },
  ]

  const statCards = [
    { label: 'Likes Given',    value: detail.totalLikes,        panel: 'all-swiped'    as RightPanel, color: 'text-green-600',   icon: <Heart size={13}/> },
    { label: 'Dislikes',       value: detail.totalDislikes,     panel: 'all-swiped'    as RightPanel, color: 'text-red-500',     icon: <HeartCrack size={13}/> },
    { label: 'Super Likes',    value: detail.totalSuperLikes,   panel: 'all-swiped'    as RightPanel, color: 'text-purple-600',  icon: <Zap size={13}/> },
    { label: 'Matches',        value: detail.totalMatches,      panel: null,                           color: 'text-pink-600',    icon: <Users size={13}/> },
    { label: 'Mood Posts',     value: detail.totalMoodPosts,    panel: null,                           color: 'text-blue-600',    icon: <FileText size={13}/> },
    { label: 'Liked By',       value: '→',                      panel: 'liked-by'      as RightPanel, color: 'text-emerald-600', icon: <Heart size={13}/> },
    { label: 'Disliked By',    value: '→',                      panel: 'disliked-by'   as RightPanel, color: 'text-orange-500',  icon: <HeartCrack size={13}/> },
    { label: 'Super Liked By', value: '→',                      panel: 'super-liked-by'as RightPanel, color: 'text-violet-600',  icon: <Zap size={13}/> },
  ]

  const rightPanelTitle = rightPanel === 'liked-by' ? 'Who Liked This User'
    : rightPanel === 'disliked-by' ? 'Who Disliked This User'
    : rightPanel === 'super-liked-by' ? 'Who Super Liked This User'
    : 'All Swipes On This User'

  return (
    <div className="p-6 space-y-5 max-w-7xl">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft size={16}/> Back to Users
      </button>

      {/* ── Header + Right Panel layout ────────────────────────────────────── */}
      <div className="flex gap-5 items-start">

        {/* Left: profile card */}
        <div className="flex-1 min-w-0 space-y-5">

          {/* Profile card */}
          <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-6 flex gap-6 flex-wrap">
            {/* Photos */}
            <div className="shrink-0">
              <div className="w-36 h-36 rounded-xl overflow-hidden bg-slate-100">
                {photos.length > 0
                  ? <img src={photos[selectedPhoto]} alt="profile" className="w-full h-full object-cover"/>
                  : <div className="w-full h-full flex items-center justify-center text-slate-300"><Image size={36}/></div>}
              </div>
              {photos.length > 1 && (
                <div className="flex gap-1 mt-2 flex-wrap max-w-[144px]">
                  {photos.map((url, i) => (
                    <button key={i} onClick={() => setSelectedPhoto(i)}
                      className={`w-7 h-7 rounded overflow-hidden border-2 ${i === selectedPhoto ? 'border-purple-500' : 'border-transparent'}`}>
                      <img src={url} alt="" className="w-full h-full object-cover"/>
                    </button>
                  ))}
                </div>
              )}
              <p className="text-xs text-slate-400 mt-1">{photos.length} photo{photos.length !== 1 ? 's' : ''}</p>
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-2xl font-bold text-slate-800">{detail.name || '—'}</h2>
                {detail.isVerified && <ShieldCheck size={16} className="text-blue-500"/>}
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  detail.subscriptionType === 'ULTRA' ? 'bg-yellow-100 text-yellow-700' :
                  detail.subscriptionType === 'PREMIUM' ? 'bg-purple-100 text-purple-700' :
                  'bg-slate-100 text-slate-600'}`}>{detail.subscriptionType}</span>
              </div>
              <p className="text-slate-500 text-sm mt-1">
                {detail.age && `${detail.age} yrs`}{detail.gender && ` • ${detail.gender}`}{detail.city && ` • ${detail.city}`}
              </p>
              {detail.mobile && <p className="text-slate-500 text-sm mt-1">📱 {detail.mobile}</p>}
              <p className="text-slate-400 text-xs mt-0.5 font-mono">ID: {detail.userId}</p>
              {detail.referralCode && (
                <p className="text-slate-400 text-xs mt-0.5">
                  Referral: <span className="font-mono font-semibold text-purple-600 bg-purple-50 px-1.5 py-0.5 rounded">{detail.referralCode}</span>
                </p>
              )}
              {detail.bio && <p className="text-slate-600 text-sm mt-2 italic">"{detail.bio}"</p>}
              {(detail.interests ?? []).length > 0 && (
                <div className="flex flex-wrap gap-1 mt-2">
                  {detail.interests.map(tag => (
                    <span key={tag} className="bg-purple-50 text-purple-600 text-xs px-2 py-0.5 rounded-full">{tag}</span>
                  ))}
                </div>
              )}
              <p className="text-xs text-slate-400 mt-1.5">
                Registered: {detail.registeredAt ? new Date(detail.registeredAt).toLocaleString() : '—'}
              </p>
              <div className="mt-2 inline-flex items-center gap-1.5 bg-amber-50 border border-amber-200 rounded-lg px-2.5 py-1">
                <Coins size={13} className="text-amber-500"/>
                <span className="text-sm font-semibold text-amber-700">{currentBalance} Points</span>
              </div>
            </div>

            {/* Stat cards — clickable → right panel */}
            <div className="shrink-0 grid grid-cols-2 gap-2 content-start">
              {statCards.map(s => (
                <button key={s.label}
                  onClick={() => s.panel ? openRightPanel(rightPanel === s.panel ? null : s.panel) : undefined}
                  className={`bg-slate-50 rounded-lg px-3 py-2 text-left transition-all ${
                    s.panel ? 'hover:bg-purple-50 hover:border-purple-200 border border-transparent cursor-pointer' : 'cursor-default border border-transparent'
                  } ${rightPanel === s.panel ? 'bg-purple-50 border-purple-300' : ''}`}
                >
                  <p className={`flex items-center gap-1 text-xs font-medium ${s.color}`}>{s.icon}{s.label}</p>
                  <p className="text-xl font-bold text-slate-800 mt-0.5">{s.value}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Points section */}
          <div className="bg-white rounded-xl shadow-sm border border-amber-100 p-5 space-y-4">
            {/* Give / Remove */}
            <div>
              <div className="flex items-center gap-2 mb-2">
                <Coins size={15} className="text-amber-500"/>
                <span className="text-sm font-semibold text-slate-700">Points</span>
                <span className="text-xs text-slate-400">Balance: <b className="text-amber-600">{currentBalance}</b></span>
                <div className="ml-auto flex gap-1">
                  <button onClick={() => setRemoveMode(false)}
                    className={`px-3 py-1 rounded-lg text-xs font-medium transition-all ${!removeMode ? 'bg-amber-500 text-white' : 'text-slate-500 hover:bg-slate-50'}`}>
                    Give
                  </button>
                  <button onClick={() => setRemoveMode(true)}
                    className={`px-3 py-1 rounded-lg text-xs font-medium transition-all ${removeMode ? 'bg-red-500 text-white' : 'text-slate-500 hover:bg-slate-50'}`}>
                    Remove
                  </button>
                </div>
              </div>
              <div className="flex gap-2 flex-wrap">
                <input type="number" min={1} value={rewardAmount} onChange={e => setRewardAmount(e.target.value)}
                  placeholder="Points" className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-28 focus:outline-none focus:border-amber-400"/>
                <input type="text" value={rewardReason} onChange={e => setRewardReason(e.target.value)}
                  placeholder="Reason (optional)" className="border border-slate-200 rounded-lg px-3 py-2 text-sm flex-1 min-w-[140px] focus:outline-none focus:border-amber-400"/>
                <button onClick={handlePoints} disabled={rewardLoading || !rewardAmount}
                  className={`flex items-center gap-1.5 px-4 py-2 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors ${removeMode ? 'bg-red-500 hover:bg-red-600' : 'bg-amber-500 hover:bg-amber-600'}`}>
                  {removeMode ? <Minus size={13}/> : <Send size={13}/>}
                  {rewardLoading ? '…' : removeMode ? 'Remove' : 'Give'}
                </button>
              </div>
              {rewardMsg && <p className={`text-xs mt-1.5 font-medium ${rewardMsg.startsWith('✓') ? 'text-green-600' : 'text-red-500'}`}>{rewardMsg}</p>}
            </div>

            {/* Gift points to another user */}
            <div className="border-t border-amber-100 pt-4">
              <div className="flex items-center gap-2 mb-2">
                <Gift size={14} className="text-purple-500"/>
                <span className="text-sm font-semibold text-slate-700">Gift Points to Another User</span>
              </div>
              <div className="flex gap-2 flex-wrap items-start">
                <div className="relative">
                  <input type="text" value={giftCode} onChange={e => handleGiftCodeChange(e.target.value)}
                    placeholder="Referral code" className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-36 focus:outline-none focus:border-purple-400"/>
                  {giftLookupLoading && <span className="absolute right-2 top-2.5 text-xs text-slate-400">…</span>}
                  {giftRecipient && (
                    <p className="text-xs text-green-600 mt-0.5 font-semibold">✓ {giftRecipient.name}</p>
                  )}
                  {giftCode.length >= 4 && !giftRecipient && !giftLookupLoading && (
                    <p className="text-xs text-red-400 mt-0.5">Code not found</p>
                  )}
                </div>
                <input type="number" min={1} value={giftAmount} onChange={e => setGiftAmount(e.target.value)}
                  placeholder="Amount" className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-24 focus:outline-none focus:border-purple-400"/>
                <input type="text" value={giftReason} onChange={e => setGiftReason(e.target.value)}
                  placeholder="Reason" className="border border-slate-200 rounded-lg px-3 py-2 text-sm flex-1 min-w-[120px] focus:outline-none focus:border-purple-400"/>
                <button onClick={handleGiftPoints} disabled={giftLoading || !giftRecipient || !giftAmount}
                  className="flex items-center gap-1.5 px-4 py-2 bg-purple-500 hover:bg-purple-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium">
                  <Gift size={13}/>{giftLoading ? '…' : 'Gift'}
                </button>
              </div>
              {giftMsg && <p className={`text-xs mt-1.5 font-medium ${giftMsg.startsWith('✓') ? 'text-green-600' : 'text-red-500'}`}>{giftMsg}</p>}
            </div>
          </div>

          {/* Daily Stats */}
          {dailyStats.length > 0 && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-center gap-2 mb-3">
                <TrendingUp size={15} className="text-blue-500"/>
                <span className="text-sm font-semibold text-slate-700">Daily Activity (Last 7 Days)</span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-slate-400 border-b border-slate-100">
                      <th className="pb-2 text-left font-medium">Date</th>
                      <th className="pb-2 text-right font-medium">Swipes</th>
                      <th className="pb-2 text-right font-medium">Likes→</th>
                      <th className="pb-2 text-right font-medium">Dislikes→</th>
                      <th className="pb-2 text-right font-medium">Liked←</th>
                      <th className="pb-2 text-right font-medium">Disliked←</th>
                      <th className="pb-2 text-right font-medium">Matches</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {dailyStats.map(d => (
                      <tr key={d.date} className="text-slate-600">
                        <td className="py-1.5 font-mono text-slate-400">{d.date}</td>
                        <td className="py-1.5 text-right font-medium">{d.swipesGiven || '—'}</td>
                        <td className="py-1.5 text-right text-green-600">{d.likesGiven || '—'}</td>
                        <td className="py-1.5 text-right text-red-400">{d.dislikesGiven || '—'}</td>
                        <td className="py-1.5 text-right text-emerald-600 font-semibold">{d.likesReceived || '—'}</td>
                        <td className="py-1.5 text-right text-orange-400">{d.dislikesReceived || '—'}</td>
                        <td className="py-1.5 text-right text-pink-600 font-semibold">{d.matchesMade || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Session / App Usage Stats */}
          {sessionStats.some(s => s.sessions > 0) && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5">
              <div className="flex items-center gap-2 mb-3">
                <span className="text-base">📱</span>
                <span className="text-sm font-semibold text-slate-700">App Usage (Last 7 Days)</span>
                <span className="text-xs text-slate-400 ml-auto">How long this user keeps the app open per day</span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-slate-400 border-b border-slate-100">
                      <th className="pb-2 text-left font-medium">Date</th>
                      <th className="pb-2 text-right font-medium">Sessions</th>
                      <th className="pb-2 text-right font-medium">Total Time</th>
                      <th className="pb-2 text-right font-medium">Avg/Session</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {sessionStats.map(s => (
                      <tr key={s.date} className="text-slate-600">
                        <td className="py-1.5 font-mono text-slate-400">{s.date}</td>
                        <td className="py-1.5 text-right">{s.sessions || '—'}</td>
                        <td className="py-1.5 text-right font-medium text-blue-600">
                          {s.totalMinutes > 0 ? `${s.totalMinutes}m` : '—'}
                        </td>
                        <td className="py-1.5 text-right text-slate-500">
                          {s.avgMinutesPerSession > 0 ? `${s.avgMinutesPerSession}m` : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Tabs */}
          <div className="flex gap-1 bg-white border border-slate-100 rounded-xl p-1 flex-wrap">
            {TABS.map(t => (
              <button key={t.key} onClick={() => setTab(t.key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  tab === t.key ? 'bg-gradient-to-r from-pink-500 to-purple-600 text-white shadow' : 'text-slate-500 hover:bg-slate-50'}`}>
                {t.label} ({t.count})
              </button>
            ))}
          </div>

          {/* ── Mood Posts ── */}
          {tab === 'posts' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Mood Posts</span>
                <select value={postSort} onChange={e => setPostSort(e.target.value)}
                  className="text-xs border border-slate-200 rounded-lg px-2 py-1.5 focus:outline-none">
                  {SORT_OPTIONS.map(o => <option key={o} value={o}>Sort: {o}</option>)}
                </select>
              </div>
              <div className="divide-y divide-slate-50">
                {posts.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No posts</p>}
                {posts.map(p => (
                  <div key={p.id} className="px-5 py-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <span className="text-xs font-semibold text-purple-600 bg-purple-50 px-2 py-0.5 rounded-full">{p.moodType}</span>
                        {p.description && <p className="text-sm text-slate-700 mt-1.5">{p.description}</p>}
                        {p.locationName && <p className="text-xs text-slate-400 mt-1">📍 {p.locationName}</p>}
                      </div>
                      <div className="flex gap-3 text-xs text-slate-500 shrink-0">
                        <span className="flex items-center gap-1"><Heart size={11} className="text-green-500"/>{p.likeCount}</span>
                        <span className="flex items-center gap-1"><HeartCrack size={11} className="text-red-400"/>{p.dislikeCount}</span>
                        <span>💬 {p.commentCount}</span>
                      </div>
                    </div>
                    <p className="text-xs text-slate-300 mt-1">{p.createdAt ? new Date(p.createdAt).toLocaleString() : ''}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Swipe Activity ── */}
          {tab === 'swipes' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Swipe Activity</span>
                <div className="flex gap-1">
                  {SWIPE_ACTIONS.map(a => (
                    <button key={a} onClick={() => setSwipeAction(a)}
                      className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-all ${swipeAction === a ? 'bg-purple-600 text-white' : 'text-slate-500 hover:bg-slate-50'}`}>
                      {a}
                    </button>
                  ))}
                </div>
              </div>
              <div className="divide-y divide-slate-50">
                {swipes.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No swipes</p>}
                {swipes.map((s, i) => (
                  <div key={i} className="flex items-center justify-between px-5 py-3">
                    <div>
                      <p className="text-sm font-medium text-slate-700">{s.toUserName || 'Unknown'}</p>
                      <p className="font-mono text-xs text-slate-400">{s.toUserId}</p>
                    </div>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[s.action] ?? ''}`}>{s.action}</span>
                    <span className="text-xs text-slate-300">{s.createdAt ? new Date(s.createdAt).toLocaleString() : ''}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Interactions Given ── */}
          {tab === 'interactions' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Interactions Given (on others' posts)</span>
              </div>
              <div className="divide-y divide-slate-50">
                {interactions.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No interactions</p>}
                {interactions.map((pi, i) => (
                  <div key={i} className="px-5 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[pi.type]}`}>
                            {pi.type === 'LIKE' ? '👍 LIKE' : pi.type === 'DISLIKE' ? '👎 DISLIKE' : '💬 COMMENT'}
                          </span>
                          {pi.moodOwnerUserId && (
                            <span className="text-xs text-slate-400">
                              on post by <span className="font-semibold text-slate-600">{pi.moodOwnerName || pi.moodOwnerUserId.slice(0,8)+'…'}</span>
                            </span>
                          )}
                        </div>
                        {pi.moodDescription && <p className="text-xs text-slate-500 mt-1 truncate">"{pi.moodDescription}"</p>}
                        {pi.type === 'COMMENT' && pi.comment && <p className="text-sm text-slate-700 mt-1 bg-slate-50 rounded px-2 py-1">"{pi.comment}"</p>}
                      </div>
                      <span className="text-xs text-slate-300 shrink-0">{pi.createdAt ? new Date(pi.createdAt).toLocaleString() : ''}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Post Engagements ── */}
          {tab === 'engagements' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Post Engagements (who liked/disliked/commented on this user's posts)</span>
              </div>
              <div className="divide-y divide-slate-50">
                {engagements.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No engagements</p>}
                {engagements.map((e, i) => (
                  <div key={i} className="px-5 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[e.type]}`}>
                            {e.type === 'LIKE' ? '👍' : e.type === 'DISLIKE' ? '👎' : '💬'} {e.type}
                          </span>
                          <span className="text-xs text-slate-400">by <span className="font-semibold text-slate-600">{e.interactorName || e.interactorUserId.slice(0,8)+'…'}</span></span>
                          {e.moodType && <span className="text-xs font-semibold text-purple-600 bg-purple-50 px-2 py-0.5 rounded-full">{e.moodType}</span>}
                        </div>
                        {e.postDescription && <p className="text-xs text-slate-500 mt-1 truncate">"{e.postDescription}"</p>}
                        {e.type === 'COMMENT' && e.comment && <p className="text-sm text-slate-700 mt-1 bg-slate-50 rounded px-2 py-1">"{e.comment}"</p>}
                      </div>
                      <span className="text-xs text-slate-300 shrink-0">{e.createdAt ? new Date(e.createdAt).toLocaleString() : ''}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Matches ── */}
          {tab === 'matches' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Matches</span>
              </div>
              <div className="divide-y divide-slate-50">
                {matches.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No matches</p>}
                {matches.map((m, i) => (
                  <div key={i} className="flex items-center justify-between px-5 py-3">
                    <div className="flex items-center gap-3">
                      <Users size={14} className="text-pink-500 shrink-0"/>
                      <div>
                        <p className="text-sm font-medium text-slate-700">{m.otherUserName || 'Unknown'}</p>
                        <p className="font-mono text-xs text-slate-400">{m.otherUserId}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${m.isActive ? 'bg-green-100 text-green-700' : 'bg-red-50 text-red-500'}`}>
                        {m.isActive ? 'Matched' : 'Unmatched'}
                      </span>
                      <span className="text-xs text-slate-300">{m.matchedAt ? new Date(m.matchedAt).toLocaleString() : '—'}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Chats ── */}
          {tab === 'chats' && (
            <div className="bg-white rounded-xl shadow-sm border border-slate-100">
              <div className="px-5 py-3 border-b border-slate-100">
                <span className="text-sm font-semibold text-slate-700">Chat Conversations</span>
              </div>
              <div className="divide-y divide-slate-50">
                {chats.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No conversations</p>}
                {chats.map((c, i) => {
                  const isExpanded = expandedChat === c.conversationId
                  const msgs = chatMessages[c.conversationId] ?? []
                  const isLoadingMsgs = chatMsgLoading === c.conversationId
                  return (
                    <div key={i}>
                      <button className="w-full px-5 py-4 text-left hover:bg-slate-50 transition-colors"
                        onClick={() => handleExpandChat(c.conversationId)}>
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-center gap-3 min-w-0 flex-1">
                            <MessageCircle size={15} className="text-blue-400 shrink-0"/>
                            <div className="min-w-0">
                              <p className="text-sm font-semibold text-slate-700">
                                {c.otherUserName && c.otherUserName !== c.otherUserId ? c.otherUserName : 'Unknown User'}
                              </p>
                              <p className="font-mono text-xs text-slate-400 truncate">{c.otherUserId}</p>
                              {c.lastMessage && (
                                <p className="text-xs text-slate-500 mt-0.5 truncate">
                                  {c.lastMessageType && c.lastMessageType !== 'TEXT' ? `[${c.lastMessageType}]` : `"${c.lastMessage}"`}
                                </p>
                              )}
                            </div>
                          </div>
                          <div className="flex flex-col items-end gap-1 shrink-0">
                            <span className="text-xs text-slate-300">{c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString() : '—'}</span>
                            {isExpanded ? <ChevronUp size={13} className="text-slate-400"/> : <ChevronDown size={13} className="text-slate-400"/>}
                          </div>
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="bg-slate-50 border-t border-slate-100 px-5 py-4 max-h-96 overflow-y-auto">
                          {isLoadingMsgs && <p className="text-center text-slate-400 text-sm py-4">Loading…</p>}
                          {!isLoadingMsgs && msgs.length === 0 && <p className="text-center text-slate-400 text-sm py-4">No messages</p>}
                          {!isLoadingMsgs && msgs.length > 0 && (
                            <div className="space-y-2">
                              {[...msgs].reverse().map((msg, mi) => {
                                const isSelf = msg.senderId === id
                                return (
                                  <div key={mi} className={`flex ${isSelf ? 'justify-end' : 'justify-start'}`}>
                                    <div className={`max-w-[70%] rounded-xl px-3 py-2 text-sm ${isSelf ? 'bg-purple-500 text-white' : 'bg-white border border-slate-200 text-slate-700'}`}>
                                      {!isSelf && <p className="text-xs font-semibold mb-0.5 text-purple-600">{msg.senderName || c.otherUserName}</p>}
                                      {(!msg.type || msg.type === 'TEXT') ? <p>{msg.text}</p> : <p className="italic opacity-75">[{msg.type}]</p>}
                                      <p className={`text-xs mt-0.5 ${isSelf ? 'text-purple-200' : 'text-slate-300'}`}>
                                        {msg.sentAt ? new Date(msg.sentAt).toLocaleString() : ''}
                                        {isSelf && (msg.seen ? ' ✓✓' : ' ✓')}
                                      </p>
                                    </div>
                                  </div>
                                )
                              })}
                            </div>
                          )}
                          <p className="text-center text-xs text-slate-300 mt-3">Auto-refreshes every 10s</p>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* ── Right Panel — who swiped this user ─────────────────────────────── */}
        {rightPanel && (
          <div className="w-72 shrink-0 bg-white rounded-xl shadow-sm border border-slate-100 self-start sticky top-4">
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
              <span className="text-sm font-semibold text-slate-700">{rightPanelTitle}</span>
              <button onClick={() => setRightPanel(null)} className="text-slate-400 hover:text-slate-600">
                <X size={15}/>
              </button>
            </div>
            <div className="max-h-[70vh] overflow-y-auto divide-y divide-slate-50">
              {rightPanelLoading && <p className="text-center text-slate-400 py-8 text-sm">Loading…</p>}
              {!rightPanelLoading && rightPanelUsers.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No results</p>}
              {!rightPanelLoading && rightPanelUsers.map((u, i) => (
                <div key={i} className="px-4 py-3">
                  <p className="text-sm font-medium text-slate-700">{u.name || 'Unknown'}</p>
                  <p className="font-mono text-xs text-slate-400 truncate">{u.userId}</p>
                  <div className="flex items-center justify-between mt-1">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[u.action] ?? ''}`}>{u.action}</span>
                    <span className="text-xs text-slate-300">{u.createdAt ? new Date(u.createdAt).toLocaleString() : ''}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
