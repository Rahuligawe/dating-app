package com.rahul.adminservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AdminDtos {

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardStats {
        private long onlineUsers;
        private long totalUsers;
        private long newSignupsToday;
        private long freeUsers;
        private long premiumUsers;
        private long ultraUsers;
        private long totalMatches;
        private long totalSwipes;
        private long swipesToday;
        private long totalMoodPosts;
        private long adImpressionsToday;
        private long totalAdImpressions;
    }

    // ── Growth Chart ──────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserGrowth {
        private List<String> dates;
        private List<Long>   signups;
        private List<Long>   likes;
        private List<Long>   matches;
    }

    // ── Revenue ───────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueStats {
        private long   premiumCount;
        private long   ultraCount;
        private double premiumRevenue;    // INR
        private double ultraRevenue;      // INR
        private double estimatedMonthlyRevenue; // INR total
        private long   totalAdImpressions;
        private double avgAdWatchSeconds;
        private long   totalAdWatchHours;
        private List<RegionStat> adRegions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegionStat {
        private String region;
        private long   impressions;
        private double watchHours;
    }

    // ── User List ─────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserSummary {
        private String  userId;
        private String  name;
        private Integer age;
        private String  gender;
        private String  city;
        private String  subscriptionType;
        private String  profilePhotoUrl;
        private boolean isVerified;
        private boolean isActive;
        private String  registeredAt;
    }

    // ── User Detail ───────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserDetail {
        private String       userId;
        private String       name;
        private Integer      age;
        private String       gender;
        private String       city;
        private String       bio;
        private String       subscriptionType;
        private List<String> photos;
        private List<String> interests;
        private boolean      isVerified;
        private String       mobile;
        private String       registeredAt;
        // Swipe activity
        private long totalLikes;
        private long totalDislikes;
        private long totalSuperLikes;
        // Match activity
        private long totalMatches;
        // Mood posts
        private long totalMoodPosts;
        private long moodLikesReceived;
        private long moodDislikesReceived;
        private long moodCommentsReceived;
        // Points wallet
        private double pointsBalance;
        // Referral
        private String referralCode;
    }

    // ── Mood Post ─────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MoodPostSummary {
        private String  id;
        private String  moodType;
        private String  description;
        private String  locationName;
        private int     likeCount;
        private int     dislikeCount;
        private int     commentCount;
        private String  createdAt;
        private boolean isActive;
    }

    // ── Swipe Activity ────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SwipeActivity {
        private String toUserId;
        private String toUserName;
        private String action;
        private String createdAt;
    }

    // ── Post Interaction (by this user on others' posts) ─────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PostInteraction {
        private String moodId;
        private String type;             // LIKE | DISLIKE | COMMENT
        private String comment;          // non-null only for COMMENT
        private String commentId;        // non-null only for COMMENT — used for admin delete
        private String createdAt;
        private String moodOwnerUserId;  // who posted it
        private String moodOwnerName;    // resolved name
        private String moodDescription;  // post preview
    }

    // ── Match Info ────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MatchInfo {
        private String  matchId;
        private String  otherUserId;
        private String  otherUserName;
        private String  matchedAt;
        private boolean isActive;
    }

    // ── Chat Summary (for admin view of user's conversations) ────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatSummary {
        private String conversationId;
        private String otherUserId;
        private String otherUserName;
        private String lastMessage;
        private String lastMessageType;
        private String lastMessageAt;
    }

    // ── Chat Message (full message detail for admin view) ─────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChatMessage {
        private String  id;
        private String  senderId;
        private String  senderName;
        private String  type;
        private String  text;
        private String  mediaUrl;
        private String  sentAt;
        private boolean seen;
    }

    // ── Post Engagement — who liked/disliked/commented on THIS user's posts ──

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PostEngagement {
        private String postId;
        private String moodType;
        private String postDescription;
        private String interactorUserId;
        private String interactorName;
        private String type;       // LIKE | DISLIKE | COMMENT
        private String comment;
        private String commentId;  // non-null only for COMMENT — used for admin delete
        private String createdAt;
    }

    // ── Reward / Remove Points Request ───────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RewardPointsRequest {
        private int    amount;
        private String reason;
    }

    // ── Gift Points Request (from one user to another via referral code) ──────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GiftPointsRequest {
        private String recipientReferralCode;
        private int    amount;
        private String reason;
    }

    // ── User Lookup (by referral code) ────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserLookup {
        private String  userId;
        private String  name;
        private String  referralCode;
        private boolean found;
    }

    // ── Daily Activity Stat ───────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyStat {
        private String date;
        private long   swipesGiven;
        private long   likesGiven;
        private long   dislikesGiven;
        private long   likesReceived;   // others swiped LIKE on this user
        private long   dislikesReceived;
        private long   matchesMade;
    }

    // ── Stat User Entry (who liked/disliked/swiped this user) ────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatUser {
        private String userId;
        private String name;
        private String action;
        private String createdAt;
    }

    // ── Session Stats (per-day app usage for one user) ───────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SessionDailyStat {
        private String date;
        private long   sessions;
        private long   totalMinutes;
        private long   avgMinutesPerSession;
    }

    // ── Top Session Users (for dashboard) ─────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopSessionUser {
        private String userId;
        private String name;
        private long   totalMinutes;
        private long   sessions;
    }

    // ── Create User (admin-initiated) ─────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateUserRequest {
        private String name;
        private String mobile;
    }

    // ── Ad Impression (inbound from Android app) ──────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AdImpressionRequest {
        private String adType;           // BANNER | INTERSTITIAL | REWARDED
        private int    watchTimeSeconds;
        private String region;           // IN | US | UK | OTHER
    }

    // ── Ad Stats ─────────────────────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdStats {
        private long          totalImpressions;
        private long          impressionsToday;
        private double        avgWatchTimeSeconds;
        private long          totalWatchTimeHours;
        private List<RegionStat> byRegion;
    }
}
