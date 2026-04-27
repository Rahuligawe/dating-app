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
        private String type;      // LIKE | DISLIKE | COMMENT
        private String comment;
        private String createdAt;
    }

    // ── Reward Points Request (admin → user) ──────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RewardPointsRequest {
        private int    amount;
        private String reason;
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
