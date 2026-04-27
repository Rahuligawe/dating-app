import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  getUserDetail, getUserPosts, getUserSwipes, getUserPostInteractions,
  getUserMatches, getUserChats, getUserPostEngagements, giveRewardPoints,
  getChatMessages,
  UserDetail, MoodPost, SwipeActivity, PostInteraction, MatchInfo,
  ChatSummary, PostEngagement, ChatMessage,
} from '../api/adminApi'
import {
  ArrowLeft, Heart, HeartCrack, Zap, Users, FileText,
  ShieldCheck, Image, Coins, Send, MessageCircle, ChevronDown, ChevronUp,
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
  const [tab,          setTab]          = useState<Tab>('posts')
  const [postSort,     setPostSort]     = useState('date')
  const [swipeAction,  setSwipeAction]  = useState('ALL')
  const [selectedPhoto, setSelectedPhoto] = useState(0)
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState('')

  // Chat message expand state
  const [expandedChat,    setExpandedChat]    = useState<string | null>(null)
  const [chatMessages,    setChatMessages]    = useState<Record<string, ChatMessage[]>>({})
  const [chatMsgLoading,  setChatMsgLoading]  = useState<string | null>(null)

  // Reward points form state
  const [rewardAmount, setRewardAmount] = useState('')
  const [rewardReason, setRewardReason] = useState('')
  const [rewardLoading, setRewardLoading] = useState(false)
  const [rewardMsg,    setRewardMsg]    = useState('')
  const [currentBalance, setCurrentBalance] = useState(0)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError('')
    Promise.all([
      getUserDetail(id),
      getUserPosts(id, postSort),
      getUserSwipes(id, swipeAction),
      getUserPostInteractions(id),
      getUserPostEngagements(id),
      getUserMatches(id),
      getUserChats(id),
    ]).then(([d, p, s, pi, pe, m, c]) => {
      setDetail(d)
      setCurrentBalance(d.pointsBalance ?? 0)
      setPosts(p)
      setSwipes(s)
      setInteractions(pi)
      setEngagements(pe)
      setMatches(m)
      setChats(c)
    }).catch(err => {
      setError(err?.response?.data?.error || 'User not found')
    }).finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    getUserPosts(id, postSort).then(setPosts).catch(console.error)
  }, [id, postSort])

  useEffect(() => {
    if (!id) return
    getUserSwipes(id, swipeAction).then(setSwipes).catch(console.error)
  }, [id, swipeAction])

  const handleExpandChat = async (conversationId: string) => {
    if (expandedChat === conversationId) {
      setExpandedChat(null)
      return
    }
    setExpandedChat(conversationId)
    if (chatMessages[conversationId]) return
    setChatMsgLoading(conversationId)
    try {
      const msgs = await getChatMessages(conversationId)
      setChatMessages(prev => ({ ...prev, [conversationId]: msgs }))
    } catch (e) {
      console.error('Failed to load messages', e)
    } finally {
      setChatMsgLoading(null)
    }
  }

  const handleGivePoints = async () => {
    if (!id || !rewardAmount || isNaN(Number(rewardAmount)) || Number(rewardAmount) <= 0) return
    setRewardLoading(true)
    setRewardMsg('')
    try {
      const res = await giveRewardPoints(id, Number(rewardAmount), rewardReason || 'Admin reward')
      setCurrentBalance(res.newBalance)
      setRewardMsg(`✓ ${res.credited} points credited! New balance: ${res.newBalance}`)
      setRewardAmount('')
      setRewardReason('')
    } catch (e: any) {
      setRewardMsg('✗ ' + (e?.response?.data?.error || 'Failed to give points'))
    } finally {
      setRewardLoading(false)
    }
  }

  if (loading) return <div className="flex items-center justify-center h-64 text-slate-400">Loading profile…</div>
  if (error || !detail) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-slate-500">{error || 'User not found'}</p>
      <button onClick={() => navigate(-1)} className="text-sm text-purple-600 hover:underline">← Back</button>
    </div>
  )

  const photos = detail.photos.length > 0 ? detail.photos : []

  const TABS: { key: Tab; label: string; count: number }[] = [
    { key: 'posts',        label: 'Mood Posts',          count: posts.length },
    { key: 'swipes',       label: 'Swipe Activity',      count: swipes.length },
    { key: 'interactions', label: 'Interactions Given',  count: interactions.length },
    { key: 'engagements',  label: 'Post Engagements',    count: engagements.length },
    { key: 'matches',      label: 'Matches',             count: matches.length },
    { key: 'chats',        label: 'Chats',               count: chats.length },
  ]

  return (
    <div className="p-6 space-y-5 max-w-5xl">
      {/* Back */}
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft size={16} /> Back to Users
      </button>

      {/* Header card */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-6 flex gap-6 flex-wrap">
        {/* Photos */}
        <div className="shrink-0">
          <div className="w-40 h-40 rounded-xl overflow-hidden bg-slate-100">
            {photos.length > 0
              ? <img src={photos[selectedPhoto]} alt="profile" className="w-full h-full object-cover" />
              : <div className="w-full h-full flex items-center justify-center text-slate-300"><Image size={40}/></div>
            }
          </div>
          {photos.length > 1 && (
            <div className="flex gap-1.5 mt-2 flex-wrap max-w-[160px]">
              {photos.map((url, i) => (
                <button key={i} onClick={() => setSelectedPhoto(i)}
                  className={`w-8 h-8 rounded overflow-hidden border-2 transition-all ${i === selectedPhoto ? 'border-purple-500' : 'border-transparent'}`}
                >
                  <img src={url} alt="" className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
          )}
          <p className="text-xs text-slate-400 mt-1.5">{photos.length} photo{photos.length !== 1 ? 's' : ''}</p>
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="text-2xl font-bold text-slate-800">{detail.name || '—'}</h2>
            {detail.isVerified && <ShieldCheck size={18} className="text-blue-500" />}
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
              detail.subscriptionType === 'ULTRA'   ? 'bg-yellow-100 text-yellow-700' :
              detail.subscriptionType === 'PREMIUM' ? 'bg-purple-100 text-purple-700' :
              'bg-slate-100 text-slate-600'
            }`}>{detail.subscriptionType}</span>
          </div>
          <p className="text-slate-500 text-sm mt-1">
            {detail.age && `${detail.age} yrs`}{detail.gender && ` • ${detail.gender}`}{detail.city && ` • ${detail.city}`}
          </p>
          {detail.mobile && <p className="text-slate-500 text-sm mt-1">📱 {detail.mobile}</p>}
          <p className="text-slate-400 text-xs mt-0.5 font-mono">ID: {detail.userId}</p>
          {detail.bio && <p className="text-slate-600 text-sm mt-2 italic">"{detail.bio}"</p>}
          {detail.interests.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-3">
              {detail.interests.map(tag => (
                <span key={tag} className="bg-purple-50 text-purple-600 text-xs px-2.5 py-0.5 rounded-full font-medium">{tag}</span>
              ))}
            </div>
          )}
          <p className="text-xs text-slate-400 mt-2">
            Registered: {detail.registeredAt ? new Date(detail.registeredAt).toLocaleString() : '—'}
          </p>

          {/* Points wallet */}
          <div className="mt-3 inline-flex items-center gap-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-1.5">
            <Coins size={14} className="text-amber-500" />
            <span className="text-sm font-semibold text-amber-700">{currentBalance} Points</span>
          </div>
        </div>

        {/* Activity stats */}
        <div className="shrink-0 grid grid-cols-2 gap-3 content-start">
          {[
            { label: 'Likes Given',   value: detail.totalLikes,       icon: <Heart size={14}/>,       color: 'text-green-600'  },
            { label: 'Dislikes',      value: detail.totalDislikes,     icon: <HeartCrack size={14}/>,  color: 'text-red-500'    },
            { label: 'Super Likes',   value: detail.totalSuperLikes,   icon: <Zap size={14}/>,         color: 'text-purple-600' },
            { label: 'Matches',       value: detail.totalMatches,      icon: <Users size={14}/>,       color: 'text-pink-600'   },
            { label: 'Mood Posts',    value: detail.totalMoodPosts,    icon: <FileText size={14}/>,    color: 'text-blue-600'   },
            { label: 'Post Likes Rx', value: detail.moodLikesReceived, icon: <Heart size={14}/>,       color: 'text-emerald-600'},
          ].map(s => (
            <div key={s.label} className="bg-slate-50 rounded-lg px-3 py-2">
              <p className={`flex items-center gap-1 text-xs font-medium ${s.color}`}>{s.icon}{s.label}</p>
              <p className="text-xl font-bold text-slate-800 mt-0.5">{s.value}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Reward Points Section */}
      <div className="bg-white rounded-xl shadow-sm border border-amber-100 p-5">
        <div className="flex items-center gap-2 mb-3">
          <Coins size={16} className="text-amber-500" />
          <span className="text-sm font-semibold text-slate-700">Give Reward Points</span>
          <span className="text-xs text-slate-400">Current balance: <b className="text-amber-600">{currentBalance}</b></span>
        </div>
        <div className="flex gap-3 flex-wrap">
          <input
            type="number"
            min={1}
            value={rewardAmount}
            onChange={e => setRewardAmount(e.target.value)}
            placeholder="Points (e.g. 100)"
            className="border border-slate-200 rounded-lg px-3 py-2 text-sm w-36 focus:outline-none focus:border-amber-400"
          />
          <input
            type="text"
            value={rewardReason}
            onChange={e => setRewardReason(e.target.value)}
            placeholder="Reason (optional)"
            className="border border-slate-200 rounded-lg px-3 py-2 text-sm flex-1 min-w-[180px] focus:outline-none focus:border-amber-400"
          />
          <button
            onClick={handleGivePoints}
            disabled={rewardLoading || !rewardAmount}
            className="flex items-center gap-2 px-4 py-2 bg-amber-500 hover:bg-amber-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors"
          >
            <Send size={14} />
            {rewardLoading ? 'Sending…' : 'Give Points'}
          </button>
        </div>
        {rewardMsg && (
          <p className={`text-xs mt-2 font-medium ${rewardMsg.startsWith('✓') ? 'text-green-600' : 'text-red-500'}`}>
            {rewardMsg}
          </p>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-white border border-slate-100 rounded-xl p-1 flex-wrap">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-all ${
              tab === t.key ? 'bg-gradient-to-r from-pink-500 to-purple-600 text-white shadow' : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>

      {/* ── Mood Posts tab ─────────────────────────────────────────────────────── */}
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
                    <span className="flex items-center gap-1"><Heart size={12} className="text-green-500"/>{p.likeCount}</span>
                    <span className="flex items-center gap-1"><HeartCrack size={12} className="text-red-400"/>{p.dislikeCount}</span>
                    <span>💬 {p.commentCount}</span>
                  </div>
                </div>
                <p className="text-xs text-slate-300 mt-1">{p.createdAt ? new Date(p.createdAt).toLocaleString() : ''}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Swipe Activity tab ─────────────────────────────────────────────────── */}
      {tab === 'swipes' && (
        <div className="bg-white rounded-xl shadow-sm border border-slate-100">
          <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
            <span className="text-sm font-semibold text-slate-700">Swipe Activity</span>
            <div className="flex gap-1">
              {SWIPE_ACTIONS.map(a => (
                <button key={a} onClick={() => setSwipeAction(a)}
                  className={`px-3 py-1 rounded-lg text-xs font-medium transition-all ${
                    swipeAction === a ? 'bg-purple-600 text-white' : 'text-slate-500 hover:bg-slate-50'
                  }`}
                >
                  {a}
                </button>
              ))}
            </div>
          </div>
          <div className="divide-y divide-slate-50">
            {swipes.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No swipes found</p>}
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

      {/* ── Interactions Given tab (user interacted on others' posts) ─────────── */}
      {tab === 'interactions' && (
        <div className="bg-white rounded-xl shadow-sm border border-slate-100">
          <div className="px-5 py-3 border-b border-slate-100">
            <span className="text-sm font-semibold text-slate-700">Interactions Given (likes/dislikes/comments on others' posts)</span>
          </div>
          <div className="divide-y divide-slate-50">
            {interactions.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No interactions</p>}
            {interactions.map((pi, i) => (
              <div key={i} className="px-5 py-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[pi.type]}`}>
                        {pi.type === 'LIKE' ? '👍 LIKE' : pi.type === 'DISLIKE' ? '👎 DISLIKE' : '💬 COMMENT'}
                      </span>
                      {pi.moodOwnerUserId && (
                        <span className="text-xs text-slate-400">
                          on post by <span className="font-semibold text-slate-600">{pi.moodOwnerName || pi.moodOwnerUserId.slice(0, 8) + '…'}</span>
                        </span>
                      )}
                    </div>
                    {pi.moodDescription && (
                      <p className="text-xs text-slate-500 mt-1 truncate">Post: "{pi.moodDescription}"</p>
                    )}
                    {pi.type === 'COMMENT' && pi.comment && (
                      <p className="text-sm text-slate-700 mt-1 bg-slate-50 rounded px-2 py-1">"{pi.comment}"</p>
                    )}
                  </div>
                  <span className="text-xs text-slate-300 shrink-0">
                    {pi.createdAt ? new Date(pi.createdAt).toLocaleString() : ''}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Post Engagements tab (who interacted on THIS user's posts) ─────────── */}
      {tab === 'engagements' && (
        <div className="bg-white rounded-xl shadow-sm border border-slate-100">
          <div className="px-5 py-3 border-b border-slate-100">
            <span className="text-sm font-semibold text-slate-700">Post Engagements (who liked/disliked/commented on this user's posts)</span>
          </div>
          <div className="divide-y divide-slate-50">
            {engagements.length === 0 && <p className="text-center text-slate-400 py-8 text-sm">No engagements yet</p>}
            {engagements.map((e, i) => (
              <div key={i} className="px-5 py-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${ACTION_COLOR[e.type]}`}>
                        {e.type === 'LIKE' ? '👍 LIKE' : e.type === 'DISLIKE' ? '👎 DISLIKE' : '💬 COMMENT'}
                      </span>
                      <span className="text-xs text-slate-400">by <span className="font-semibold text-slate-600">{e.interactorName || e.interactorUserId.slice(0, 8) + '…'}</span></span>
                      {e.moodType && (
                        <span className="text-xs font-semibold text-purple-600 bg-purple-50 px-2 py-0.5 rounded-full">{e.moodType}</span>
                      )}
                    </div>
                    {e.postDescription && (
                      <p className="text-xs text-slate-500 mt-1 truncate">Post: "{e.postDescription}"</p>
                    )}
                    {e.type === 'COMMENT' && e.comment && (
                      <p className="text-sm text-slate-700 mt-1 bg-slate-50 rounded px-2 py-1">"{e.comment}"</p>
                    )}
                  </div>
                  <span className="text-xs text-slate-300 shrink-0">
                    {e.createdAt ? new Date(e.createdAt).toLocaleString() : ''}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Matches tab ────────────────────────────────────────────────────────── */}
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
                    {m.isActive ? 'Active' : 'Unmatched'}
                  </span>
                  <span className="text-xs text-slate-300">
                    {m.matchedAt ? new Date(m.matchedAt).toLocaleString() : '—'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Chats tab ──────────────────────────────────────────────────────────── */}
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
                  {/* Conversation row — click to expand */}
                  <button
                    className="w-full px-5 py-4 text-left hover:bg-slate-50 transition-colors"
                    onClick={() => handleExpandChat(c.conversationId)}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3 min-w-0 flex-1">
                        <MessageCircle size={16} className="text-blue-400 shrink-0" />
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-slate-700">
                            {c.otherUserName && c.otherUserName !== c.otherUserId
                              ? c.otherUserName
                              : 'Unknown User'}
                          </p>
                          <p className="font-mono text-xs text-slate-400 truncate">{c.otherUserId}</p>
                          {c.lastMessage && (
                            <p className="text-xs text-slate-500 mt-0.5 truncate">
                              {c.lastMessageType && c.lastMessageType !== 'TEXT'
                                ? `[${c.lastMessageType}]`
                                : `"${c.lastMessage}"`}
                            </p>
                          )}
                        </div>
                      </div>
                      <div className="flex flex-col items-end gap-1 shrink-0">
                        <span className="text-xs text-slate-300">
                          {c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString() : '—'}
                        </span>
                        {isExpanded
                          ? <ChevronUp size={14} className="text-slate-400" />
                          : <ChevronDown size={14} className="text-slate-400" />}
                      </div>
                    </div>
                  </button>

                  {/* Expanded message history */}
                  {isExpanded && (
                    <div className="bg-slate-50 border-t border-slate-100 px-5 py-4 max-h-96 overflow-y-auto">
                      {isLoadingMsgs && (
                        <p className="text-center text-slate-400 text-sm py-4">Loading messages…</p>
                      )}
                      {!isLoadingMsgs && msgs.length === 0 && (
                        <p className="text-center text-slate-400 text-sm py-4">No messages</p>
                      )}
                      {!isLoadingMsgs && msgs.length > 0 && (
                        <div className="space-y-2">
                          {[...msgs].reverse().map((msg, mi) => {
                            const isSelf = msg.senderId === id
                            return (
                              <div key={mi} className={`flex ${isSelf ? 'justify-end' : 'justify-start'}`}>
                                <div className={`max-w-[70%] rounded-xl px-3 py-2 text-sm ${
                                  isSelf
                                    ? 'bg-purple-500 text-white'
                                    : 'bg-white border border-slate-200 text-slate-700'
                                }`}>
                                  {!isSelf && (
                                    <p className="text-xs font-semibold mb-0.5 text-purple-600">
                                      {msg.senderName || c.otherUserName || 'User'}
                                    </p>
                                  )}
                                  {msg.type === 'TEXT' || !msg.type
                                    ? <p>{msg.text}</p>
                                    : <p className="italic opacity-75">[{msg.type}]</p>}
                                  <p className={`text-xs mt-1 ${isSelf ? 'text-purple-200' : 'text-slate-300'}`}>
                                    {msg.sentAt ? new Date(msg.sentAt).toLocaleString() : ''}
                                    {isSelf && (msg.seen ? ' ✓✓' : ' ✓')}
                                  </p>
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
