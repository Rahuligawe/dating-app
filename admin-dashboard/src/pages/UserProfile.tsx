import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  getUserDetail, getUserPosts, getUserSwipes, getUserPostInteractions,
  getUserMatches, getUserChats, getUserPostEngagements, giveRewardPoints,
  getChatMessages, removePoints, getUserByReferralCode, giftPoints,
  getDailyStats, getWhoSwipedUser, getSessionStats, createChatStream, deleteComment,
  UserDetail, MoodPost, SwipeActivity, PostInteraction, MatchInfo,
  ChatSummary, PostEngagement, ChatMessage, DailyStat, SessionDailyStat,
} from '../api/adminApi'
import {
  ArrowLeft, Heart, HeartCrack, Zap, Users, FileText,
  ShieldCheck, Image, Send, MessageCircle,
  ChevronDown, ChevronUp, X, TrendingUp, Gift, Minus, Trash2,
  Star, Clock, Coins,
} from 'lucide-react'

/* ─── Constants ─────────────────────────────────────────────────────────── */
const SORT_OPTIONS  = ['date', 'likes', 'dislikes', 'comments']
const SWIPE_ACTIONS = ['ALL', 'LIKE', 'DISLIKE', 'SUPER_LIKE']

const ACTION_STYLE: Record<string, string> = {
  LIKE:       'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
  DISLIKE:    'bg-red-500/10 text-red-400 border border-red-500/20',
  SUPER_LIKE: 'bg-violet-500/10 text-violet-400 border border-violet-500/20',
  COMMENT:    'bg-blue-500/10 text-blue-400 border border-blue-500/20',
}

type Tab = 'posts' | 'swipes' | 'interactions' | 'engagements' | 'matches' | 'chats'
type RightPanel =
  | null
  | 'likes-given' | 'dislikes-given' | 'super-likes-given'
  | 'matches' | 'mood-posts'
  | 'liked-by' | 'disliked-by' | 'super-liked-by'

type RightItem = {
  id: string
  label: string
  sublabel?: string
  badge?: string
  badgeStyle?: string
  date?: string
}

const RIGHT_PANEL_TITLES: Record<NonNullable<RightPanel>, string> = {
  'likes-given':       'People They Liked',
  'dislikes-given':    'People They Disliked',
  'super-likes-given': 'People They Super Liked',
  'matches':           'Matches',
  'mood-posts':        'Mood Posts',
  'liked-by':          'People Who Liked Them',
  'disliked-by':       'People Who Disliked Them',
  'super-liked-by':    'People Who Super Liked Them',
}

/* ─── Inline Styles (dark theme tokens) ─────────────────────────────────── */
const S = {
  page:        { background: '#0A0A0F', minHeight: '100vh', padding: '24px 28px', color: '#F0EDE8', fontFamily: "'Outfit', 'Inter', sans-serif" } as React.CSSProperties,
  card:        { background: '#111118', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16 } as React.CSSProperties,
  cardInner:   { background: '#16161F', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12 } as React.CSSProperties,
  accent:      '#C9A96E',
  accentFaint: 'rgba(201,169,110,0.12)',
  muted:       '#8A8799',
  muted2:      '#5C5A6E',
  border:      'rgba(255,255,255,0.07)',
  border2:     'rgba(255,255,255,0.12)',
  red:         '#E05C6B',
  green:       '#4ECFA0',
  blue:        '#5B9EF0',
  violet:      '#9B7FE8',
  text:        '#F0EDE8',
}

/* ─── Sub-components ─────────────────────────────────────────────────────── */
function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <p style={{ fontSize: 10, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '1.2px', color: S.muted2, marginBottom: 12 }}>
      {children}
    </p>
  )
}

function DarkBadge({ children, color = S.accent }: { children: React.ReactNode; color?: string }) {
  return (
    <span style={{
      fontSize: 10, fontWeight: 600, padding: '3px 10px', borderRadius: 20,
      background: `${color}18`, color, border: `1px solid ${color}33`,
      textTransform: 'uppercase', letterSpacing: '0.6px', display: 'inline-block',
    }}>
      {children}
    </span>
  )
}

function ActionBadge({ action }: { action: string }) {
  const styles: Record<string, { bg: string; color: string }> = {
    LIKE:       { bg: 'rgba(78,207,160,0.1)',  color: '#4ECFA0' },
    DISLIKE:    { bg: 'rgba(224,92,107,0.1)',  color: '#E05C6B' },
    SUPER_LIKE: { bg: 'rgba(155,127,232,0.1)', color: '#9B7FE8' },
    COMMENT:    { bg: 'rgba(91,158,240,0.1)',  color: '#5B9EF0' },
  }
  const s = styles[action] ?? { bg: 'rgba(255,255,255,0.05)', color: S.muted }
  return (
    <span style={{ fontSize: 10, fontWeight: 600, padding: '3px 10px', borderRadius: 20, background: s.bg, color: s.color, border: `1px solid ${s.color}33` }}>
      {action}
    </span>
  )
}

function DarkInput({ placeholder, value, onChange, type = 'text', style }: {
  placeholder?: string; value: string; onChange: (v: string) => void; type?: string; style?: React.CSSProperties
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      style={{
        background: '#1C1C28', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10,
        padding: '9px 14px', fontSize: 13, color: S.text, fontFamily: 'inherit', outline: 'none',
        ...style,
      }}
      onFocus={e => (e.target.style.borderColor = S.accent)}
      onBlur={e => (e.target.style.borderColor = 'rgba(255,255,255,0.1)')}
    />
  )
}

function GoldButton({ onClick, disabled, children, style }: {
  onClick?: () => void; disabled?: boolean; children: React.ReactNode; style?: React.CSSProperties
}) {
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: 'linear-gradient(135deg,#C9A96E,#A0784A)', color: '#fff', border: 'none',
      borderRadius: 10, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.45 : 1, fontFamily: 'inherit', transition: 'filter 0.2s', ...style,
    }}>
      {children}
    </button>
  )
}

function GhostButton({ onClick, disabled, children, danger, style }: {
  onClick?: () => void; disabled?: boolean; children: React.ReactNode; danger?: boolean; style?: React.CSSProperties
}) {
  const color = danger ? 'rgba(224,92,107,0.25)' : 'rgba(255,255,255,0.08)'
  const textColor = danger ? S.red : S.muted
  return (
    <button onClick={onClick} disabled={disabled} style={{
      background: 'transparent', border: `1px solid ${danger ? 'rgba(224,92,107,0.3)' : S.border2}`,
      borderRadius: 10, padding: '9px 16px', fontSize: 13, fontWeight: 500, cursor: disabled ? 'not-allowed' : 'pointer',
      color: textColor, opacity: disabled ? 0.45 : 1, fontFamily: 'inherit', transition: 'background 0.2s', ...style,
    }}
      onMouseEnter={e => ((e.target as HTMLElement).style.background = color)}
      onMouseLeave={e => ((e.target as HTMLElement).style.background = 'transparent')}
    >
      {children}
    </button>
  )
}

/* ─── Main Component ─────────────────────────────────────────────────────── */
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
  const [dailyStats,   setDailyStats]   = useState<DailyStat[]>([])
  const [sessionStats, setSessionStats] = useState<SessionDailyStat[]>([])
  const [tab,          setTab]          = useState<Tab>('posts')
  const [postSort,     setPostSort]     = useState('date')
  const [swipeAction,  setSwipeAction]  = useState('ALL')
  const [selectedPhoto,setSelectedPhoto]= useState(0)
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState('')

  // Slideshow auto-advance
  const slideTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // Right panel
  const [rightPanel,        setRightPanel]        = useState<RightPanel>(null)
  const [rightPanelItems,   setRightPanelItems]   = useState<RightItem[]>([])
  const [rightPanelLoading, setRightPanelLoading] = useState(false)

  // Chat
  const [expandedChat,   setExpandedChat]   = useState<string | null>(null)
  const [chatMessages,   setChatMessages]   = useState<Record<string, ChatMessage[]>>({})
  const [chatMsgLoading, setChatMsgLoading] = useState<string | null>(null)

  // Points
  const [rewardAmount,  setRewardAmount]  = useState('')
  const [rewardReason,  setRewardReason]  = useState('')
  const [rewardLoading, setRewardLoading] = useState(false)
  const [rewardMsg,     setRewardMsg]     = useState('')
  const [currentBalance,setCurrentBalance]= useState(0)
  const [removeMode,    setRemoveMode]    = useState(false)

  // Gift points
  const [giftCode,          setGiftCode]          = useState('')
  const [giftAmount,        setGiftAmount]        = useState('')
  const [giftReason,        setGiftReason]        = useState('')
  const [giftRecipient,     setGiftRecipient]     = useState<{ name: string; userId: string } | null>(null)
  const [giftLookupLoading, setGiftLookupLoading] = useState(false)
  const [giftLoading,       setGiftLoading]       = useState(false)
  const [giftMsg,           setGiftMsg]           = useState('')
  const giftCodeTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [deletingComment, setDeletingComment] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    Promise.all([
      getUserDetail(id), getUserPosts(id, postSort), getUserSwipes(id, swipeAction),
      getUserPostInteractions(id), getUserPostEngagements(id), getUserMatches(id),
      getUserChats(id), getDailyStats(id, 7), getSessionStats(id, 7),
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

  // Auto-advance slideshow every 4s
  useEffect(() => {
    const photos = detail?.photos ?? []
    if (photos.length <= 1) return
    slideTimer.current = setInterval(() => setSelectedPhoto(p => (p + 1) % photos.length), 4000)
    return () => { if (slideTimer.current) clearInterval(slideTimer.current) }
  }, [detail?.photos])

  const openRightPanel = async (type: NonNullable<RightPanel>) => {
    if (!id) return
    setRightPanel(type)
    setRightPanelLoading(true)
    setRightPanelItems([])
    try {
      switch (type) {
        case 'likes-given': {
          const data = await getUserSwipes(id, 'LIKE', 100)
          setRightPanelItems(data.map(s => ({ id: s.toUserId, label: s.toUserName || 'Unknown', sublabel: s.toUserId, badge: 'LIKE', badgeStyle: 'bg-green like', date: s.createdAt })))
          break
        }
        case 'dislikes-given': {
          const data = await getUserSwipes(id, 'DISLIKE', 100)
          setRightPanelItems(data.map(s => ({ id: s.toUserId, label: s.toUserName || 'Unknown', sublabel: s.toUserId, badge: 'DISLIKE', date: s.createdAt })))
          break
        }
        case 'super-likes-given': {
          const data = await getUserSwipes(id, 'SUPER_LIKE', 100)
          setRightPanelItems(data.map(s => ({ id: s.toUserId, label: s.toUserName || 'Unknown', sublabel: s.toUserId, badge: 'SUPER LIKE', date: s.createdAt })))
          break
        }
        case 'matches':
          setRightPanelItems(matches.map(m => ({ id: m.otherUserId, label: m.otherUserName || 'Unknown', sublabel: m.otherUserId, date: m.matchedAt })))
          break
        case 'mood-posts':
          setRightPanelItems(posts.map(p => ({ id: p.id, label: p.moodType, sublabel: p.description ? `"${p.description.slice(0, 60)}${p.description.length > 60 ? '…' : ''}"` : undefined, badge: `❤️ ${p.likeCount}  💬 ${p.commentCount}`, date: p.createdAt })))
          break
        case 'liked-by': {
          const data = await getWhoSwipedUser(id, 'LIKE', 100)
          setRightPanelItems(data.map(u => ({ id: u.userId, label: u.name || 'Unknown', sublabel: u.userId, badge: 'LIKED', date: u.createdAt })))
          break
        }
        case 'disliked-by': {
          const data = await getWhoSwipedUser(id, 'DISLIKE', 100)
          setRightPanelItems(data.map(u => ({ id: u.userId, label: u.name || 'Unknown', sublabel: u.userId, badge: 'DISLIKED', date: u.createdAt })))
          break
        }
        case 'super-liked-by': {
          const data = await getWhoSwipedUser(id, 'SUPER_LIKE', 100)
          setRightPanelItems(data.map(u => ({ id: u.userId, label: u.name || 'Unknown', sublabel: u.userId, badge: 'SUPER LIKED', date: u.createdAt })))
          break
        }
      }
    } catch { setRightPanelItems([]) }
    finally { setRightPanelLoading(false) }
  }

  const sseCleanupRef = useRef<(() => void) | null>(null)
  const handleExpandChat = async (conversationId: string) => {
    if (expandedChat === conversationId) {
      setExpandedChat(null)
      sseCleanupRef.current?.(); sseCleanupRef.current = null
      return
    }
    sseCleanupRef.current?.(); sseCleanupRef.current = null
    setExpandedChat(conversationId)
    setChatMsgLoading(conversationId)
    try {
      const msgs = await getChatMessages(conversationId)
      setChatMessages(prev => ({ ...prev, [conversationId]: msgs }))
    } catch { /* ignore */ }
    setChatMsgLoading(null)
    sseCleanupRef.current = createChatStream(conversationId, (incoming) => {
      setChatMessages(prev => {
        const existing = prev[conversationId] ?? []
        const msgId = incoming['id'] as string | undefined
        if (msgId && existing.some((m: ChatMessage) => m.id === msgId)) return prev
        return { ...prev, [conversationId]: [incoming as unknown as ChatMessage, ...existing] }
      })
    })
  }
  useEffect(() => () => { sseCleanupRef.current?.() }, [])

  const handleGiftCodeChange = (code: string) => {
    setGiftCode(code); setGiftRecipient(null)
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
      setGiftMsg(`✓ ${res.gifted} pts gifted to ${res.toUserName}! New balance: ${res.senderNewBalance}`)
      setGiftCode(''); setGiftAmount(''); setGiftReason(''); setGiftRecipient(null)
    } catch (e: any) {
      setGiftMsg('✗ ' + (e?.response?.data?.error || 'Gift failed'))
    } finally { setGiftLoading(false) }
  }

  const handleDeleteComment = async (commentId: string) => {
    if (!commentId) return
    setDeletingComment(commentId)
    try {
      await deleteComment(commentId)
      setEngagements(prev => prev.filter(e => e.commentId !== commentId))
      setInteractions(prev => prev.filter(i => i.commentId !== commentId))
    } catch (e: any) {
      alert('Failed to delete: ' + (e?.response?.data?.error || 'Error'))
    } finally { setDeletingComment(null) }
  }

  /* ── Loading / Error ── */
  if (loading) return (
    <div style={{ ...S.page, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ width: 40, height: 40, border: `2px solid ${S.accentFaint}`, borderTopColor: S.accent, borderRadius: '50%', margin: '0 auto 12px', animation: 'spin 0.8s linear infinite' }} />
        <p style={{ color: S.muted, fontSize: 14 }}>Loading profile…</p>
        <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
      </div>
    </div>
  )
  if (error || !detail) return (
    <div style={{ ...S.page, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
      <p style={{ color: S.muted }}>{error || 'User not found'}</p>
      <button onClick={() => navigate(-1)} style={{ color: S.accent, background: 'none', border: 'none', cursor: 'pointer', fontSize: 14 }}>← Back</button>
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

  const statCards: { label: string; value: string | number; panel: NonNullable<RightPanel>; icon: React.ReactNode; color: string }[] = [
    { label: 'Likes Given',    value: detail.totalLikes,      panel: 'likes-given',       icon: <Heart size={12} />,      color: S.green },
    { label: 'Dislikes Given', value: detail.totalDislikes,   panel: 'dislikes-given',    icon: <HeartCrack size={12} />, color: S.red },
    { label: 'Super Likes',    value: detail.totalSuperLikes, panel: 'super-likes-given', icon: <Zap size={12} />,        color: S.violet },
    { label: 'Matches',        value: detail.totalMatches,    panel: 'matches',           icon: <Users size={12} />,      color: '#E06B9A' },
    { label: 'Mood Posts',     value: detail.totalMoodPosts,  panel: 'mood-posts',        icon: <FileText size={12} />,   color: S.blue },
    { label: 'Liked By',       value: '→',                    panel: 'liked-by',          icon: <Heart size={12} />,      color: S.green },
    { label: 'Disliked By',    value: '→',                    panel: 'disliked-by',       icon: <HeartCrack size={12} />, color: S.red },
    { label: 'Super Liked By', value: '→',                    panel: 'super-liked-by',    icon: <Zap size={12} />,        color: S.violet },
  ]

  /* ── Plan badge config ── */
  const planConfig = detail.subscriptionType === 'ULTRA'
    ? { color: '#F59E0B', label: '⚡ ULTRA' }
    : detail.subscriptionType === 'PREMIUM'
    ? { color: S.accent, label: '★ PREMIUM' }
    : { color: S.muted, label: 'FREE' }

  return (
    <div style={S.page}>
      {/* Google Fonts */}
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Outfit:wght@300;400;500;600&display=swap');
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 4px; height: 4px; }
        ::-webkit-scrollbar-track { background: #0A0A0F; }
        ::-webkit-scrollbar-thumb { background: #2A2A3A; border-radius: 4px; }
        input::placeholder { color: #5C5A6E; }
        @keyframes spin { to { transform: rotate(360deg); } }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
        .profile-wrap { animation: fadeIn 0.4s ease; }
        .stat-btn:hover { border-color: rgba(201,169,110,0.4) !important; background: rgba(201,169,110,0.06) !important; }
        .stat-btn.active-panel { border-color: rgba(201,169,110,0.5) !important; background: rgba(201,169,110,0.1) !important; }
        .tab-btn { transition: all 0.2s; }
        .tab-btn:hover { color: #F0EDE8 !important; }
        .row-hover:hover { background: rgba(255,255,255,0.03) !important; }
        .nav-arr:hover { background: rgba(201,169,110,0.2) !important; border-color: rgba(201,169,110,0.4) !important; }
        .thumb-item:hover { opacity: 1 !important; }
      `}</style>

      <div className="profile-wrap" style={{ maxWidth: 1280, margin: '0 auto' }}>

        {/* ── Back button ── */}
        <button onClick={() => navigate(-1)} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'none', border: 'none', color: S.muted, cursor: 'pointer', fontSize: 13, fontWeight: 500, marginBottom: 20, padding: 0, fontFamily: 'inherit' }}>
          <ArrowLeft size={15} />
          Back to Users
        </button>

        {/* ── HERO ROW: Slideshow + Profile Info ── */}
        <div style={{ display: 'flex', gap: 20, marginBottom: 20, alignItems: 'flex-start', flexWrap: 'wrap' }}>

          {/* SLIDESHOW */}
          <div style={{ ...S.card, width: 280, flexShrink: 0, overflow: 'hidden' }}>
            <div style={{ position: 'relative', width: '100%', aspectRatio: '3/4', background: '#000', overflow: 'hidden' }}>
              {photos.length > 0 ? (
                photos.map((url, i) => (
                  <div key={i} style={{ position: 'absolute', inset: 0, opacity: i === selectedPhoto ? 1 : 0, transition: 'opacity 0.7s ease', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  </div>
                ))
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%', height: '100%', color: S.muted2 }}>
                  <Image size={48} />
                </div>
              )}

              {/* Gradient overlays */}
              <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(10,10,15,0.85) 0%, rgba(10,10,15,0.1) 45%, transparent 70%)', pointerEvents: 'none' }} />
              <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 70, background: 'linear-gradient(to bottom, rgba(10,10,15,0.5), transparent)', pointerEvents: 'none' }} />

              {/* Counter */}
              {photos.length > 0 && (
                <div style={{ position: 'absolute', top: 12, right: 12, background: 'rgba(10,10,15,0.6)', backdropFilter: 'blur(8px)', color: 'rgba(255,255,255,0.8)', fontSize: 11, fontWeight: 500, padding: '4px 10px', borderRadius: 20, border: '1px solid rgba(255,255,255,0.1)' }}>
                  {selectedPhoto + 1} / {photos.length}
                </div>
              )}

              {/* Subscription badge top-left */}
              <div style={{ position: 'absolute', top: 12, left: 12 }}>
                <DarkBadge color={planConfig.color}>{planConfig.label}</DarkBadge>
              </div>

              {/* Name overlay */}
              <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, padding: '14px 16px 16px' }}>
                <p style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 24, fontWeight: 600, color: '#fff', lineHeight: 1.1, textShadow: '0 2px 12px rgba(0,0,0,0.5)' }}>
                  {detail.name || '—'}
                </p>
                <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.65)', marginTop: 3 }}>
                  {[detail.age && `${detail.age} yrs`, detail.gender, detail.city].filter(Boolean).join(' · ')}
                </p>

                {/* Dots */}
                {photos.length > 1 && (
                  <div style={{ display: 'flex', gap: 5, marginTop: 10 }}>
                    {photos.map((_, i) => (
                      <div key={i} onClick={() => setSelectedPhoto(i)} style={{ width: i === selectedPhoto ? 18 : 4, height: 4, borderRadius: 3, background: i === selectedPhoto ? S.accent : 'rgba(255,255,255,0.35)', cursor: 'pointer', transition: 'all 0.35s' }} />
                    ))}
                  </div>
                )}
              </div>

              {/* Nav arrows */}
              {photos.length > 1 && (
                <div style={{ position: 'absolute', bottom: 16, right: 12, display: 'flex', gap: 6 }}>
                  <button className="nav-arr" onClick={() => setSelectedPhoto(p => (p - 1 + photos.length) % photos.length)} style={{ width: 28, height: 28, borderRadius: '50%', background: 'rgba(255,255,255,0.08)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.15)', color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, transition: 'all 0.2s' }}>←</button>
                  <button className="nav-arr" onClick={() => setSelectedPhoto(p => (p + 1) % photos.length)} style={{ width: 28, height: 28, borderRadius: '50%', background: 'rgba(255,255,255,0.08)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.15)', color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, transition: 'all 0.2s' }}>→</button>
                </div>
              )}
            </div>

            {/* Thumbnail strip */}
            {photos.length > 1 && (
              <div style={{ display: 'flex', gap: 6, padding: '10px 12px', overflowX: 'auto' }}>
                {photos.map((url, i) => (
                  <div key={i} className="thumb-item" onClick={() => setSelectedPhoto(i)} style={{ width: 40, height: 52, borderRadius: 7, overflow: 'hidden', border: `2px solid ${i === selectedPhoto ? S.accent : 'transparent'}`, cursor: 'pointer', flexShrink: 0, opacity: i === selectedPhoto ? 1 : 0.5, transition: 'all 0.2s' }}>
                    <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* PROFILE META + STAT CARDS */}
          <div style={{ flex: 1, minWidth: 300, display: 'flex', flexDirection: 'column', gap: 16 }}>

            {/* Meta card */}
            <div style={{ ...S.card, padding: '20px 22px' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 14 }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                    <h2 style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 30, fontWeight: 600, color: S.text, lineHeight: 1, margin: 0 }}>{detail.name || '—'}</h2>
                    {detail.isVerified && <ShieldCheck size={16} color={S.blue} />}
                  </div>
                  <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                    <DarkBadge color={planConfig.color}>{planConfig.label}</DarkBadge>
                    {detail.gender && <DarkBadge color={S.blue}>{detail.gender}</DarkBadge>}
                    {detail.referralCode && (
                      <span style={{ fontSize: 10, fontFamily: 'monospace', color: S.muted2, border: `1px solid ${S.border}`, borderRadius: 6, padding: '3px 8px' }}>
                        REF: {detail.referralCode}
                      </span>
                    )}
                  </div>
                </div>
                {/* Points balance */}
                <div style={{ background: 'rgba(201,169,110,0.1)', border: '1px solid rgba(201,169,110,0.25)', borderRadius: 12, padding: '10px 16px', textAlign: 'center' }}>
                  <p style={{ fontSize: 10, color: S.muted2, marginBottom: 2 }}>Balance</p>
                  <p style={{ fontSize: 22, fontWeight: 700, color: S.accent, fontFamily: "'Cormorant Garamond', serif", lineHeight: 1 }}>{currentBalance}</p>
                  <p style={{ fontSize: 10, color: S.muted2, marginTop: 2 }}>Points</p>
                </div>
              </div>

              {/* Info grid */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 14 }}>
                {[
                  { icon: '📱', label: 'Phone', value: detail.mobile || '—' },
                  { icon: '📍', label: 'Location', value: detail.city || '—' },
                  { icon: '🎂', label: 'Age', value: detail.age ? `${detail.age} years` : '—' },
                  { icon: '📅', label: 'Joined', value: detail.registeredAt ? new Date(detail.registeredAt).toLocaleDateString() : '—' },
                ].map(row => (
                  <div key={row.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <div style={{ width: 28, height: 28, background: '#1C1C28', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, flexShrink: 0 }}>{row.icon}</div>
                    <div>
                      <p style={{ fontSize: 10, color: S.muted2, marginBottom: 1 }}>{row.label}</p>
                      <p style={{ fontSize: 13, color: S.muted }}>{row.value}</p>
                    </div>
                  </div>
                ))}
              </div>

              <p style={{ fontSize: 10, color: S.muted2, fontFamily: 'monospace', marginBottom: 10 }}>ID: {detail.userId}</p>

              {detail.bio && (
                <div style={{ background: '#1C1C28', borderRadius: 10, padding: '10px 14px', borderLeft: `2px solid ${S.accent}`, marginBottom: 12 }}>
                  <p style={{ fontSize: 13, color: S.muted, fontStyle: 'italic' }}>"{detail.bio}"</p>
                </div>
              )}

              {(detail.interests ?? []).length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {detail.interests.map(tag => (
                    <span key={tag} style={{ fontSize: 12, padding: '4px 12px', borderRadius: 20, background: '#1C1C28', color: S.muted, border: `1px solid ${S.border}` }}>{tag}</span>
                  ))}
                </div>
              )}
            </div>

            {/* Stat cards grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
              {statCards.map(s => (
                <button key={s.label} className={`stat-btn${rightPanel === s.panel ? ' active-panel' : ''}`} onClick={() => openRightPanel(s.panel)}
                  style={{ ...S.cardInner, padding: '12px 10px', textAlign: 'center', cursor: 'pointer', border: `1px solid ${S.border}`, transition: 'all 0.2s' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4, marginBottom: 6, color: s.color }}>
                    {s.icon}
                    <span style={{ fontSize: 10, color: S.muted2, fontWeight: 500 }}>{s.label}</span>
                  </div>
                  <p style={{ fontFamily: "'Cormorant Garamond', serif", fontSize: 26, fontWeight: 600, color: S.text, lineHeight: 1 }}>{s.value}</p>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── BOTTOM ROW: Points + Daily Stats + Tabs ── */}
        <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start', flexWrap: 'wrap' }}>

          {/* LEFT COLUMN */}
          <div style={{ flex: 1, minWidth: 300, display: 'flex', flexDirection: 'column', gap: 16 }}>

            {/* Points Management */}
            <div style={{ ...S.card, padding: '20px 22px' }}>
              <SectionTitle>Points Management</SectionTitle>

              {/* Give / Remove */}
              <div style={{ marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                  <Coins size={14} color={S.accent} />
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Give or Remove Points</span>
                  <div style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
                    <button onClick={() => setRemoveMode(false)} style={{ padding: '4px 12px', borderRadius: 8, fontSize: 11, fontWeight: 600, background: !removeMode ? S.accent : 'transparent', color: !removeMode ? '#fff' : S.muted, border: `1px solid ${!removeMode ? S.accent : S.border}`, cursor: 'pointer', transition: 'all 0.2s' }}>Give</button>
                    <button onClick={() => setRemoveMode(true)} style={{ padding: '4px 12px', borderRadius: 8, fontSize: 11, fontWeight: 600, background: removeMode ? S.red : 'transparent', color: removeMode ? '#fff' : S.muted, border: `1px solid ${removeMode ? S.red : S.border}`, cursor: 'pointer', transition: 'all 0.2s' }}>Remove</button>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <DarkInput placeholder="Amount" value={rewardAmount} onChange={setRewardAmount} type="number" style={{ width: 100 }} />
                  <DarkInput placeholder="Reason (optional)" value={rewardReason} onChange={setRewardReason} style={{ flex: 1, minWidth: 120 }} />
                  {removeMode
                    ? <GhostButton danger onClick={handlePoints} disabled={rewardLoading || !rewardAmount}><span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Minus size={13} />{rewardLoading ? '…' : 'Remove'}</span></GhostButton>
                    : <GoldButton onClick={handlePoints} disabled={rewardLoading || !rewardAmount}><span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Send size={13} />{rewardLoading ? '…' : 'Give'}</span></GoldButton>
                  }
                </div>
                {rewardMsg && <p style={{ fontSize: 12, marginTop: 8, color: rewardMsg.startsWith('✓') ? S.green : S.red, fontWeight: 500 }}>{rewardMsg}</p>}
              </div>

              {/* Divider */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '12px 0' }}>
                <div style={{ flex: 1, height: 1, background: S.border }} />
                <span style={{ fontSize: 10, color: S.muted2 }}>Gift to another user</span>
                <div style={{ flex: 1, height: 1, background: S.border }} />
              </div>

              {/* Gift points */}
              <div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                  <div style={{ position: 'relative' }}>
                    <DarkInput placeholder="Referral code" value={giftCode} onChange={handleGiftCodeChange} style={{ width: 140 }} />
                    {giftLookupLoading && <span style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', fontSize: 11, color: S.muted2 }}>…</span>}
                    {giftRecipient && <p style={{ fontSize: 11, color: S.green, marginTop: 3, fontWeight: 600 }}>✓ {giftRecipient.name}</p>}
                    {giftCode.length >= 4 && !giftRecipient && !giftLookupLoading && <p style={{ fontSize: 11, color: S.red, marginTop: 3 }}>Code not found</p>}
                  </div>
                  <DarkInput placeholder="Amount" value={giftAmount} onChange={setGiftAmount} type="number" style={{ width: 90 }} />
                  <DarkInput placeholder="Reason" value={giftReason} onChange={setGiftReason} style={{ flex: 1, minWidth: 100 }} />
                  <GoldButton onClick={handleGiftPoints} disabled={giftLoading || !giftRecipient || !giftAmount}><span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Gift size={13} />{giftLoading ? '…' : 'Gift'}</span></GoldButton>
                </div>
                {giftMsg && <p style={{ fontSize: 12, color: giftMsg.startsWith('✓') ? S.green : S.red, fontWeight: 500 }}>{giftMsg}</p>}
              </div>
            </div>

            {/* Daily Activity Table */}
            {dailyStats.length > 0 && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{ width: 7, height: 7, borderRadius: '50%', background: S.green }} />
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Daily Activity — Last 7 Days</span>
                </div>
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ background: '#16161F' }}>
                        {['Date', 'Swipes', 'Likes→', 'Dislikes→', 'Liked←', 'Disliked←', 'Matches'].map(h => (
                          <th key={h} style={{ padding: '9px 14px', textAlign: h === 'Date' ? 'left' : 'right', fontSize: 10, fontWeight: 500, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.7px' }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {dailyStats.map(d => (
                        <tr key={d.date} className="row-hover" style={{ borderTop: `1px solid ${S.border}` }}>
                          <td style={{ padding: '9px 14px', fontFamily: 'monospace', fontSize: 12, color: S.muted2 }}>{d.date}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.muted }}>{d.swipesGiven || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.green, fontWeight: 500 }}>{d.likesGiven || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.red }}>{d.dislikesGiven || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.green, fontWeight: 600 }}>{d.likesReceived || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: '#E8A87C' }}>{d.dislikesReceived || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: '#E06B9A', fontWeight: 600 }}>{d.matchesMade || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Session Stats */}
            {sessionStats.some(s => s.sessions > 0) && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Clock size={14} color={S.blue} />
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>App Usage — Last 7 Days</span>
                </div>
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ background: '#16161F' }}>
                        {['Date', 'Sessions', 'Total Time', 'Avg/Session'].map(h => (
                          <th key={h} style={{ padding: '9px 14px', textAlign: h === 'Date' ? 'left' : 'right', fontSize: 10, fontWeight: 500, color: S.muted2, textTransform: 'uppercase', letterSpacing: '0.7px' }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {sessionStats.map(s => (
                        <tr key={s.date} className="row-hover" style={{ borderTop: `1px solid ${S.border}` }}>
                          <td style={{ padding: '9px 14px', fontFamily: 'monospace', fontSize: 12, color: S.muted2 }}>{s.date}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.muted }}>{s.sessions || '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.blue, fontWeight: 500 }}>{s.totalMinutes > 0 ? `${s.totalMinutes}m` : '—'}</td>
                          <td style={{ padding: '9px 14px', textAlign: 'right', fontSize: 12, color: S.muted }}>{s.avgMinutesPerSession > 0 ? `${s.avgMinutesPerSession}m` : '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Tab bar */}
            <div style={{ ...S.card, padding: 6, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              {TABS.map(t => (
                <button key={t.key} className="tab-btn" onClick={() => setTab(t.key)} style={{
                  padding: '8px 14px', borderRadius: 10, fontSize: 12, fontWeight: 500, border: 'none', cursor: 'pointer', fontFamily: 'inherit',
                  background: tab === t.key ? 'linear-gradient(135deg,#C9A96E,#A0784A)' : 'transparent',
                  color: tab === t.key ? '#fff' : S.muted,
                  boxShadow: tab === t.key ? '0 2px 10px rgba(201,169,110,0.25)' : 'none',
                }}>
                  {t.label} <span style={{ opacity: 0.7, fontSize: 10 }}>({t.count})</span>
                </button>
              ))}
            </div>

            {/* ── Mood Posts ── */}
            {tab === 'posts' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Mood Posts</span>
                  <select value={postSort} onChange={e => setPostSort(e.target.value)} style={{ background: '#1C1C28', border: `1px solid ${S.border}`, borderRadius: 8, padding: '5px 10px', fontSize: 12, color: S.muted, fontFamily: 'inherit', outline: 'none' }}>
                    {SORT_OPTIONS.map(o => <option key={o} value={o}>Sort: {o}</option>)}
                  </select>
                </div>
                {posts.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No posts yet</p>}
                {posts.map(p => (
                  <div key={p.id} className="row-hover" style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}` }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <DarkBadge color={S.violet}>{p.moodType}</DarkBadge>
                        {p.description && <p style={{ fontSize: 13, color: S.muted, marginTop: 6, lineHeight: 1.5 }}>{p.description}</p>}
                        {p.locationName && <p style={{ fontSize: 11, color: S.muted2, marginTop: 4 }}>📍 {p.locationName}</p>}
                      </div>
                      <div style={{ display: 'flex', gap: 14, fontSize: 12, color: S.muted2, flexShrink: 0 }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: S.green }}><Heart size={11} />{p.likeCount}</span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: S.red }}><HeartCrack size={11} />{p.dislikeCount}</span>
                        <span>💬 {p.commentCount}</span>
                      </div>
                    </div>
                    <p style={{ fontSize: 11, color: S.muted2, marginTop: 6 }}>{p.createdAt ? new Date(p.createdAt).toLocaleString() : ''}</p>
                  </div>
                ))}
              </div>
            )}

            {/* ── Swipe Activity ── */}
            {tab === 'swipes' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Swipe Activity</span>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {SWIPE_ACTIONS.map(a => (
                      <button key={a} onClick={() => setSwipeAction(a)} style={{ padding: '4px 10px', borderRadius: 8, fontSize: 11, fontWeight: 500, background: swipeAction === a ? S.accent : 'transparent', color: swipeAction === a ? '#fff' : S.muted, border: `1px solid ${swipeAction === a ? S.accent : S.border}`, cursor: 'pointer', fontFamily: 'inherit', transition: 'all 0.2s' }}>{a}</button>
                    ))}
                  </div>
                </div>
                {swipes.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No swipes</p>}
                {swipes.map((s, i) => (
                  <div key={i} className="row-hover" style={{ padding: '12px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div>
                      <p style={{ fontSize: 13, fontWeight: 500, color: S.text }}>{s.toUserName || 'Unknown'}</p>
                      <p style={{ fontFamily: 'monospace', fontSize: 11, color: S.muted2, marginTop: 2 }}>{s.toUserId}</p>
                    </div>
                    <ActionBadge action={s.action} />
                    <span style={{ fontSize: 11, color: S.muted2 }}>{s.createdAt ? new Date(s.createdAt).toLocaleString() : ''}</span>
                  </div>
                ))}
              </div>
            )}

            {/* ── Interactions Given ── */}
            {tab === 'interactions' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Interactions Given (on others' posts)</span>
                </div>
                {interactions.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No interactions</p>}
                {interactions.map((pi, i) => (
                  <div key={i} className="row-hover" style={{ padding: '12px 20px', borderBottom: `1px solid ${S.border}` }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          <ActionBadge action={pi.type} />
                          {pi.moodOwnerUserId && <span style={{ fontSize: 12, color: S.muted2 }}>on post by <span style={{ color: S.muted, fontWeight: 500 }}>{pi.moodOwnerName || pi.moodOwnerUserId.slice(0, 8) + '…'}</span></span>}
                        </div>
                        {pi.moodDescription && <p style={{ fontSize: 12, color: S.muted2, marginTop: 5, fontStyle: 'italic' }}>"{pi.moodDescription}"</p>}
                        {pi.type === 'COMMENT' && pi.comment && <p style={{ fontSize: 13, color: S.muted, marginTop: 5, background: '#1C1C28', borderRadius: 8, padding: '8px 12px' }}>"{pi.comment}"</p>}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                        <span style={{ fontSize: 11, color: S.muted2 }}>{pi.createdAt ? new Date(pi.createdAt).toLocaleString() : ''}</span>
                        {pi.type === 'COMMENT' && pi.commentId && (
                          <button onClick={() => handleDeleteComment(pi.commentId!)} disabled={deletingComment === pi.commentId} style={{ padding: 5, background: 'transparent', border: 'none', cursor: 'pointer', color: S.red, opacity: deletingComment === pi.commentId ? 0.4 : 0.7, transition: 'opacity 0.2s' }}>
                            <Trash2 size={13} />
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* ── Post Engagements ── */}
            {tab === 'engagements' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Post Engagements (who engaged with this user's posts)</span>
                </div>
                {engagements.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No engagements</p>}
                {engagements.map((e, i) => (
                  <div key={i} className="row-hover" style={{ padding: '12px 20px', borderBottom: `1px solid ${S.border}` }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          <ActionBadge action={e.type} />
                          <span style={{ fontSize: 12, color: S.muted2 }}>by <span style={{ color: S.muted, fontWeight: 500 }}>{e.interactorName || e.interactorUserId.slice(0, 8) + '…'}</span></span>
                          {e.moodType && <DarkBadge color={S.violet}>{e.moodType}</DarkBadge>}
                        </div>
                        {e.postDescription && <p style={{ fontSize: 12, color: S.muted2, marginTop: 5, fontStyle: 'italic' }}>"{e.postDescription}"</p>}
                        {e.type === 'COMMENT' && e.comment && <p style={{ fontSize: 13, color: S.muted, marginTop: 5, background: '#1C1C28', borderRadius: 8, padding: '8px 12px' }}>"{e.comment}"</p>}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                        <span style={{ fontSize: 11, color: S.muted2 }}>{e.createdAt ? new Date(e.createdAt).toLocaleString() : ''}</span>
                        {e.type === 'COMMENT' && e.commentId && (
                          <button onClick={() => handleDeleteComment(e.commentId!)} disabled={deletingComment === e.commentId} style={{ padding: 5, background: 'transparent', border: 'none', cursor: 'pointer', color: S.red, opacity: deletingComment === e.commentId ? 0.4 : 0.7, transition: 'opacity 0.2s' }}>
                            <Trash2 size={13} />
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* ── Matches ── */}
            {tab === 'matches' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Matches</span>
                </div>
                {matches.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No matches yet</p>}
                {matches.map((m, i) => (
                  <div key={i} className="row-hover" style={{ padding: '12px 20px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'linear-gradient(135deg,#E06B9A,#9B7FE8)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Users size={14} color="#fff" />
                      </div>
                      <div>
                        <p style={{ fontSize: 13, fontWeight: 500, color: S.text }}>{m.otherUserName || 'Unknown'}</p>
                        <p style={{ fontFamily: 'monospace', fontSize: 11, color: S.muted2, marginTop: 1 }}>{m.otherUserId}</p>
                      </div>
                    </div>
                    <span style={{ fontSize: 11, color: S.muted2 }}>{m.matchedAt ? new Date(m.matchedAt).toLocaleString() : '—'}</span>
                  </div>
                ))}
              </div>
            )}

            {/* ── Chats ── */}
            {tab === 'chats' && (
              <div style={{ ...S.card, overflow: 'hidden' }}>
                <div style={{ padding: '14px 20px', borderBottom: `1px solid ${S.border}` }}>
                  <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>Chat Conversations</span>
                </div>
                {chats.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No conversations</p>}
                {chats.map((c, i) => {
                  const isExpanded = expandedChat === c.conversationId
                  const msgs = chatMessages[c.conversationId] ?? []
                  const isLoadingMsgs = chatMsgLoading === c.conversationId
                  return (
                    <div key={i}>
                      <button className="row-hover" style={{ width: '100%', padding: '14px 20px', textAlign: 'left', background: 'transparent', border: 'none', borderBottom: `1px solid ${S.border}`, cursor: 'pointer', fontFamily: 'inherit' }} onClick={() => handleExpandChat(c.conversationId)}>
                        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0, flex: 1 }}>
                            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'rgba(91,158,240,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                              <MessageCircle size={14} color={S.blue} />
                            </div>
                            <div style={{ minWidth: 0 }}>
                              <p style={{ fontSize: 13, fontWeight: 500, color: S.text }}>{c.otherUserName && c.otherUserName !== c.otherUserId ? c.otherUserName : 'Unknown User'}</p>
                              <p style={{ fontFamily: 'monospace', fontSize: 11, color: S.muted2, marginTop: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.otherUserId}</p>
                              {c.lastMessage && <p style={{ fontSize: 12, color: S.muted2, marginTop: 3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.lastMessageType && c.lastMessageType !== 'TEXT' ? `[${c.lastMessageType}]` : `"${c.lastMessage}"`}</p>}
                            </div>
                          </div>
                          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
                            <span style={{ fontSize: 11, color: S.muted2 }}>{c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString() : '—'}</span>
                            {isExpanded ? <ChevronUp size={13} color={S.muted2} /> : <ChevronDown size={13} color={S.muted2} />}
                          </div>
                        </div>
                      </button>

                      {isExpanded && (
                        <div style={{ background: '#0D0D14', borderBottom: `1px solid ${S.border}`, padding: '16px 20px', maxHeight: 380, overflowY: 'auto' }}>
                          {isLoadingMsgs && <p style={{ textAlign: 'center', color: S.muted2, fontSize: 13, padding: '16px 0' }}>Loading…</p>}
                          {!isLoadingMsgs && msgs.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, fontSize: 13, padding: '16px 0' }}>No messages</p>}
                          {!isLoadingMsgs && msgs.length > 0 && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                              {[...msgs].reverse().map((msg, mi) => {
                                const isSelf = msg.senderId === id
                                return (
                                  <div key={mi} style={{ display: 'flex', justifyContent: isSelf ? 'flex-end' : 'flex-start' }}>
                                    <div style={{ maxWidth: '70%', borderRadius: 12, padding: '8px 12px', background: isSelf ? 'linear-gradient(135deg,#C9A96E,#A0784A)' : '#1C1C28', border: isSelf ? 'none' : `1px solid ${S.border}` }}>
                                      {!isSelf && <p style={{ fontSize: 11, fontWeight: 600, marginBottom: 3, color: S.accent }}>{msg.senderName || c.otherUserName}</p>}
                                      <p style={{ fontSize: 13, color: isSelf ? '#fff' : S.muted, lineHeight: 1.4 }}>{(!msg.type || msg.type === 'TEXT') ? msg.text : `[${msg.type}]`}</p>
                                      <p style={{ fontSize: 10, marginTop: 4, color: isSelf ? 'rgba(255,255,255,0.5)' : S.muted2 }}>{msg.sentAt ? new Date(msg.sentAt).toLocaleString() : ''}{isSelf && (msg.seen ? ' ✓✓' : ' ✓')}</p>
                                    </div>
                                  </div>
                                )
                              })}
                            </div>
                          )}
                          <p style={{ textAlign: 'center', fontSize: 11, color: S.muted2, marginTop: 12 }}>Live via Redis · updates instantly</p>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* ── RIGHT PANEL ── */}
          {rightPanel && (
            <div style={{ ...S.card, width: 280, flexShrink: 0, position: 'sticky', top: 20, alignSelf: 'flex-start', overflow: 'hidden' }}>
              <div style={{ padding: '14px 18px', borderBottom: `1px solid ${S.border}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 13, fontWeight: 500, color: S.text }}>{RIGHT_PANEL_TITLES[rightPanel]}</span>
                <button onClick={() => setRightPanel(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: S.muted2, padding: 2, display: 'flex' }}><X size={15} /></button>
              </div>
              <div style={{ maxHeight: '70vh', overflowY: 'auto' }}>
                {rightPanelLoading && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>Loading…</p>}
                {!rightPanelLoading && rightPanelItems.length === 0 && <p style={{ textAlign: 'center', color: S.muted2, padding: '32px 0', fontSize: 13 }}>No results</p>}
                {!rightPanelLoading && rightPanelItems.map((item, i) => (
                  <div key={i} className="row-hover" style={{ padding: '12px 18px', borderBottom: `1px solid ${S.border}` }}>
                    <p style={{ fontSize: 13, fontWeight: 500, color: S.text }}>{item.label}</p>
                    {item.sublabel && <p style={{ fontFamily: 'monospace', fontSize: 11, color: S.muted2, marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.sublabel}</p>}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 6, gap: 8 }}>
                      {item.badge && <span style={{ fontSize: 10, fontWeight: 600, padding: '2px 8px', borderRadius: 12, background: S.accentFaint, color: S.accent, border: `1px solid rgba(201,169,110,0.25)` }}>{item.badge}</span>}
                      {item.date && <span style={{ fontSize: 11, color: S.muted2, flexShrink: 0 }}>{new Date(item.date).toLocaleDateString()}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
