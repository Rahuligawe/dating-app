import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
})

// Inject token from localStorage on every request
api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

// ── Types ──────────────────────────────────────────────────────────────────

export interface DashboardStats {
  onlineUsers: number
  totalUsers: number
  newSignupsToday: number
  freeUsers: number
  premiumUsers: number
  ultraUsers: number
  totalMatches: number
  totalSwipes: number
  swipesToday: number
  totalMoodPosts: number
  adImpressionsToday: number
  totalAdImpressions: number
}

export interface UserGrowth {
  dates: string[]
  signups: number[]
  likes: number[]
  matches: number[]
}

export interface RegionStat {
  region: string
  impressions: number
  watchHours: number
}

export interface RevenueStats {
  premiumCount: number
  ultraCount: number
  premiumRevenue: number
  ultraRevenue: number
  estimatedMonthlyRevenue: number
  totalAdImpressions: number
  avgAdWatchSeconds: number
  totalAdWatchHours: number
  adRegions: RegionStat[]
}

export interface UserSummary {
  userId: string
  name: string
  age: number
  gender: string
  city: string
  subscriptionType: string
  profilePhotoUrl?: string
  isVerified: boolean
  isActive: boolean
  registeredAt: string
}

export interface UserDetail {
  userId: string
  name: string
  age: number
  gender: string
  city: string
  bio: string
  subscriptionType: string
  photos: string[]
  interests: string[]
  isVerified: boolean
  mobile: string
  registeredAt: string
  totalLikes: number
  totalDislikes: number
  totalSuperLikes: number
  totalMatches: number
  totalMoodPosts: number
  moodLikesReceived: number
  moodDislikesReceived: number
  moodCommentsReceived: number
  pointsBalance: number
  referralCode?: string
}

export interface MoodPost {
  id: string
  moodType: string
  description: string
  locationName: string
  likeCount: number
  dislikeCount: number
  commentCount: number
  createdAt: string
  isActive: boolean
}

export interface SwipeActivity {
  toUserId: string
  toUserName?: string
  action: string
  createdAt: string
}

export interface PostInteraction {
  moodId: string
  type: 'LIKE' | 'DISLIKE' | 'COMMENT'
  comment?: string
  createdAt: string
  moodOwnerUserId: string
  moodOwnerName?: string
  moodDescription?: string
}

export interface MatchInfo {
  matchId: string
  otherUserId: string
  otherUserName?: string
  matchedAt: string
  isActive: boolean
}

export interface ChatSummary {
  conversationId: string
  otherUserId: string
  otherUserName?: string
  lastMessage?: string
  lastMessageType?: string
  lastMessageAt?: string
}

export interface ChatMessage {
  id: string
  senderId: string
  senderName?: string
  type: string
  text?: string
  mediaUrl?: string
  sentAt: string
  seen: boolean
}

export interface PostEngagement {
  postId: string
  moodType: string
  postDescription?: string
  interactorUserId: string
  interactorName?: string
  type: 'LIKE' | 'DISLIKE' | 'COMMENT'
  comment?: string
  createdAt: string
}

export interface UserLookup {
  userId: string
  name: string
  referralCode: string
  found: boolean
}

export interface DailyStat {
  date: string
  swipesGiven: number
  likesGiven: number
  dislikesGiven: number
  likesReceived: number
  dislikesReceived: number
  matchesMade: number
}

export interface StatUser {
  userId: string
  name?: string
  action: string
  createdAt: string
}

export interface SessionDailyStat {
  date: string
  sessions: number
  totalMinutes: number
  avgMinutesPerSession: number
}

export interface TopSessionUser {
  userId: string
  name?: string
  totalMinutes: number
  sessions: number
}

export interface AdStats {
  totalImpressions: number
  impressionsToday: number
  avgWatchTimeSeconds: number
  totalWatchTimeHours: number
  byRegion: RegionStat[]
}

// ── API calls ──────────────────────────────────────────────────────────────

export const getStats      = ()                            => api.get<DashboardStats>('/admin/stats').then(r => r.data)
export const getOnlineCount= ()                            => api.get<{count:number}>('/admin/online-count').then(r => r.data)
export const getGrowth     = (days=30)                     => api.get<UserGrowth>(`/admin/growth?days=${days}`).then(r => r.data)
export const getRevenue    = ()                            => api.get<RevenueStats>('/admin/revenue').then(r => r.data)
export const getAdStats    = ()                            => api.get<AdStats>('/admin/ads/stats').then(r => r.data)

export const getUsers = (page=0, size=20, plan='ALL', search='') =>
  api.get<UserSummary[]>(`/admin/users?page=${page}&size=${size}&plan=${plan}&search=${encodeURIComponent(search)}`).then(r => r.data)

export const getUserDetail = (userId: string)              => api.get<UserDetail>(`/admin/user/${userId}`).then(r => r.data)
export const getUserPosts  = (userId: string, sortBy='date', limit=50) =>
  api.get<MoodPost[]>(`/admin/user/${userId}/posts?sortBy=${sortBy}&limit=${limit}`).then(r => r.data)
export const getUserSwipes = (userId: string, action='ALL', limit=100) =>
  api.get<SwipeActivity[]>(`/admin/user/${userId}/swipes?action=${action}&limit=${limit}`).then(r => r.data)

export const getUserPostInteractions = (userId: string, limit = 100) =>
  api.get<PostInteraction[]>(`/admin/user/${userId}/post-interactions?limit=${limit}`).then(r => r.data)

export const getUserMatches = (userId: string, limit = 100) =>
  api.get<MatchInfo[]>(`/admin/user/${userId}/matches?limit=${limit}`).then(r => r.data)

export const getUserChats = (userId: string) =>
  api.get<ChatSummary[]>(`/admin/user/${userId}/chats`).then(r => r.data)

export const getUserPostEngagements = (userId: string, limit = 200) =>
  api.get<PostEngagement[]>(`/admin/user/${userId}/post-engagements?limit=${limit}`).then(r => r.data)

export const getChatMessages = (conversationId: string, limit = 200) =>
  api.get<ChatMessage[]>(`/admin/chats/${conversationId}/messages?limit=${limit}`).then(r => r.data)

export const giveRewardPoints = (userId: string, amount: number, reason: string) =>
  api.post<{ success: boolean; newBalance: number; credited: number }>(
    `/admin/user/${userId}/reward-points`, { amount, reason }
  ).then(r => r.data)

export const removePoints = (userId: string, amount: number, reason: string) =>
  api.post<{ success: boolean; newBalance: number }>(`/admin/user/${userId}/remove-points`, { amount, reason }).then(r => r.data)

export const getUserByReferralCode = (code: string) =>
  api.get<UserLookup>(`/admin/user/by-referral/${code}`).then(r => r.data)

export const giftPoints = (fromUserId: string, recipientReferralCode: string, amount: number, reason: string) =>
  api.post<{ success: boolean; gifted: number; toUserName: string; senderNewBalance: number }>(
    `/admin/user/${fromUserId}/gift-points`, { recipientReferralCode, amount, reason }
  ).then(r => r.data)

export const getDailyStats = (userId: string, days = 7) =>
  api.get<DailyStat[]>(`/admin/user/${userId}/daily-stats?days=${days}`).then(r => r.data)

export const getWhoSwipedUser = (userId: string, action = 'ALL', limit = 100) =>
  api.get<StatUser[]>(`/admin/user/${userId}/who-swiped?action=${action}&limit=${limit}`).then(r => r.data)

export const getSessionStats = (userId: string, days = 7) =>
  api.get<SessionDailyStat[]>(`/admin/user/${userId}/session-stats?days=${days}`).then(r => r.data)

export const getTopSessionUsers = (days = 30, limit = 10) =>
  api.get<TopSessionUser[]>(`/admin/sessions/top-users?days=${days}&limit=${limit}`).then(r => r.data)

// Real-time SSE stream for admin chat — uses fetch (not EventSource) so Authorization header works
export const createChatStream = (
  conversationId: string,
  onMessage: (msg: Record<string, unknown>) => void
): (() => void) => {
  const token = localStorage.getItem('admin_token')
  const controller = new AbortController()

  ;(async () => {
    try {
      const response = await fetch(`/api/admin/chats/${conversationId}/stream`, {
        headers: { Authorization: `Bearer ${token || ''}` },
        signal: controller.signal,
      })
      if (!response.ok || !response.body) return
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE format: "data: {...}\n\n"
        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''
        for (const part of parts) {
          const line = part.split('\n').find(l => l.startsWith('data:'))
          if (line) {
            try { onMessage(JSON.parse(line.slice(5).trim())) } catch { /* ignore */ }
          }
        }
      }
    } catch (e: unknown) {
      if (e instanceof Error && e.name !== 'AbortError') console.warn('Chat SSE disconnected', e.message)
    }
  })()

  return () => controller.abort()
}

// Auth — calls existing auth-service
export const loginAdmin = (mobile: string, otp: string) =>
  api.post('/auth/verify-otp', { mobile, otp }).then(r => r.data)
