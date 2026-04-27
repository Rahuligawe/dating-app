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

// Auth — calls existing auth-service
export const loginAdmin = (mobile: string, otp: string) =>
  api.post('/auth/verify-otp', { mobile, otp }).then(r => r.data)
