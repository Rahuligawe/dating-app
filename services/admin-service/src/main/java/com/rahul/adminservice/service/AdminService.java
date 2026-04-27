package com.rahul.adminservice.service;

import com.rahul.adminservice.dto.AdminDtos.*;
import com.rahul.adminservice.entity.AdImpression;
import com.rahul.adminservice.repository.AdImpressionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminService {

    // ── Injected via constructor (explicit — @Qualifier doesn't work with Lombok) ──

    private final JdbcTemplate userJdbc;
    private final JdbcTemplate authJdbc;
    private final JdbcTemplate subJdbc;
    private final JdbcTemplate swipeJdbc;
    private final JdbcTemplate matchJdbc;
    private final JdbcTemplate moodJdbc;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AdImpressionRepository adRepo;

    @Value("${chat.service.url:http://chat-service:8085}")
    private String chatServiceUrl;

    @Value("${subscription.service.url:http://dating-subscription:8091}")
    private String subscriptionServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Plan prices (INR) — keep in sync with SubscriptionPlan table
    private static final double PREMIUM_PRICE_INR = 299.0;
    private static final double ULTRA_PRICE_INR   = 599.0;

    public AdminService(
            @Qualifier("userJdbc")  JdbcTemplate userJdbc,
            @Qualifier("authJdbc")  JdbcTemplate authJdbc,
            @Qualifier("subJdbc")   JdbcTemplate subJdbc,
            @Qualifier("swipeJdbc") JdbcTemplate swipeJdbc,
            @Qualifier("matchJdbc") JdbcTemplate matchJdbc,
            @Qualifier("moodJdbc")  JdbcTemplate moodJdbc,
            RedisTemplate<String, Object> redisTemplate,
            AdImpressionRepository adRepo) {
        this.userJdbc   = userJdbc;
        this.authJdbc   = authJdbc;
        this.subJdbc    = subJdbc;
        this.swipeJdbc  = swipeJdbc;
        this.matchJdbc  = matchJdbc;
        this.moodJdbc   = moodJdbc;
        this.redisTemplate = redisTemplate;
        this.adRepo     = adRepo;
    }

    // ── Dashboard Stats ─────────────────────────────────────────────────────────

    public DashboardStats getDashboardStats() {
        long online     = getOnlineUserCount();
        long total      = qLong(userJdbc, "SELECT COUNT(*) FROM user_profiles WHERE is_active = true");
        long today      = qLong(authJdbc, "SELECT COUNT(*) FROM auth_users WHERE created_at::date = CURRENT_DATE");
        long free       = qLong(userJdbc, "SELECT COUNT(*) FROM user_profiles WHERE subscription_type = 'FREE'  AND is_active = true");
        long premium    = qLong(userJdbc, "SELECT COUNT(*) FROM user_profiles WHERE subscription_type = 'PREMIUM' AND is_active = true");
        long ultra      = qLong(userJdbc, "SELECT COUNT(*) FROM user_profiles WHERE subscription_type = 'ULTRA'  AND is_active = true");
        long matches    = qLong(matchJdbc, "SELECT COUNT(*) FROM matches WHERE is_active = true");
        long swipes     = qLong(swipeJdbc, "SELECT COUNT(*) FROM swipes");
        long swipesToday= qLong(swipeJdbc, "SELECT COUNT(*) FROM swipes WHERE created_at::date = CURRENT_DATE");
        long moods      = qLong(moodJdbc,  "SELECT COUNT(*) FROM mood_status");
        long adsToday   = adRepo.countSince(LocalDateTime.now().toLocalDate().atStartOfDay());
        long adsTotal   = adRepo.count();

        return DashboardStats.builder()
                .onlineUsers(online).totalUsers(total).newSignupsToday(today)
                .freeUsers(free).premiumUsers(premium).ultraUsers(ultra)
                .totalMatches(matches).totalSwipes(swipes).swipesToday(swipesToday)
                .totalMoodPosts(moods).adImpressionsToday(adsToday).totalAdImpressions(adsTotal)
                .build();
    }

    public long getOnlineUserCount() {
        try {
            Set<String> keys = redisTemplate.keys("presence:*");
            return keys == null ? 0L : keys.size();
        } catch (Exception e) {
            log.warn("Redis key scan failed: {}", e.getMessage());
            return 0L;
        }
    }

    // ── User Growth (chart data) ────────────────────────────────────────────────

    public UserGrowth getUserGrowth(int days) {
        List<String> dates   = new ArrayList<>();
        List<Long>   signups = new ArrayList<>();
        List<Long>   likes   = new ArrayList<>();
        List<Long>   matches = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).toString();
            dates.add(date);
            signups.add(qLong(authJdbc,  "SELECT COUNT(*) FROM auth_users WHERE created_at::date = ?::date", date));
            likes.add(qLong(swipeJdbc,   "SELECT COUNT(*) FROM swipes WHERE action = 'LIKE' AND created_at::date = ?::date", date));
            matches.add(qLong(matchJdbc, "SELECT COUNT(*) FROM matches WHERE matched_at::date = ?::date", date));
        }

        return UserGrowth.builder().dates(dates).signups(signups).likes(likes).matches(matches).build();
    }

    // ── Revenue ─────────────────────────────────────────────────────────────────

    public RevenueStats getRevenue() {
        long premiumCount = qLong(subJdbc, "SELECT COUNT(*) FROM user_subscriptions WHERE plan = 'PREMIUM' AND is_active = true");
        long ultraCount   = qLong(subJdbc, "SELECT COUNT(*) FROM user_subscriptions WHERE plan = 'ULTRA'   AND is_active = true");

        double premiumRev = premiumCount * PREMIUM_PRICE_INR;
        double ultraRev   = ultraCount   * ULTRA_PRICE_INR;

        long   totalImpressions = adRepo.count();
        double avgWatch         = adRepo.avgWatchTime();
        long   totalWatchSecs   = adRepo.totalWatchTimeSeconds();

        List<Object[]>   regionRows = adRepo.countByRegion();
        List<RegionStat> regions    = new ArrayList<>();
        for (Object[] row : regionRows) {
            regions.add(RegionStat.builder()
                    .region(row[0] != null ? row[0].toString() : "OTHER")
                    .impressions(toLong(row[1]))
                    .watchHours(0)
                    .build());
        }

        return RevenueStats.builder()
                .premiumCount(premiumCount).ultraCount(ultraCount)
                .premiumRevenue(premiumRev).ultraRevenue(ultraRev)
                .estimatedMonthlyRevenue(premiumRev + ultraRev)
                .totalAdImpressions(totalImpressions)
                .avgAdWatchSeconds(avgWatch)
                .totalAdWatchHours(totalWatchSecs / 3600)
                .adRegions(regions)
                .build();
    }

    // ── User List ────────────────────────────────────────────────────────────────

    public List<UserSummary> getUsers(int page, int size, String plan, String search) {
        int offset = page * size;
        String sql;
        Object[] params;

        if (search != null && !search.isBlank()) {
            sql = "SELECT id, name, age, gender, city, subscription_type, is_verified, is_active, created_at " +
                  "FROM user_profiles WHERE (name ILIKE ? OR id::text = ?) " +
                  "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            params = new Object[]{"%" + search + "%", search, size, offset};
        } else if (plan != null && !plan.isBlank() && !"ALL".equals(plan)) {
            sql = "SELECT id, name, age, gender, city, subscription_type, is_verified, is_active, created_at " +
                  "FROM user_profiles WHERE subscription_type = ? AND is_active = true " +
                  "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            params = new Object[]{plan, size, offset};
        } else {
            sql = "SELECT id, name, age, gender, city, subscription_type, is_verified, is_active, created_at " +
                  "FROM user_profiles WHERE is_active = true " +
                  "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            params = new Object[]{size, offset};
        }

        return userJdbc.query(sql, params, (rs, rn) -> UserSummary.builder()
                .userId(rs.getString("id"))
                .name(rs.getString("name"))
                .age(rs.getObject("age", Integer.class))
                .gender(rs.getString("gender"))
                .city(rs.getString("city"))
                .subscriptionType(rs.getString("subscription_type"))
                .isVerified(rs.getBoolean("is_verified"))
                .isActive(rs.getBoolean("is_active"))
                .registeredAt(Objects.toString(rs.getObject("created_at"), ""))
                .build());
    }

    // ── User Detail ──────────────────────────────────────────────────────────────

    public UserDetail getUserDetail(String userId) {
        List<Map<String, Object>> rows = userJdbc.queryForList(
                "SELECT * FROM user_profiles WHERE id::text = ?", userId);
        if (rows.isEmpty()) {
            throw new java.util.NoSuchElementException("User not found: " + userId);
        }
        Map<String, Object> profile = rows.get(0);

        // Auth info (separate DB)
        String mobile = "";
        String registeredAt = "";
        try {
            Map<String, Object> auth = authJdbc.queryForMap(
                    "SELECT mobile, created_at FROM auth_users WHERE id = ?", userId);
            mobile      = Objects.toString(auth.get("mobile"), "");
            registeredAt= Objects.toString(auth.get("created_at"), "");
        } catch (Exception ignored) {}

        // Photos (ElementCollection table)
        List<String> photos = new ArrayList<>();
        try {
            userJdbc.queryForList("SELECT photo_url FROM user_photos WHERE user_id = ?", userId)
                    .forEach(row -> photos.add((String) row.get("photo_url")));
        } catch (Exception e) {
            String ph = (String) profile.get("profile_photo_url");
            if (ph != null) photos.add(ph);
        }

        // Interests (ElementCollection table)
        List<String> interests = new ArrayList<>();
        try {
            userJdbc.queryForList("SELECT interest FROM user_interests WHERE user_id = ?", userId)
                    .forEach(row -> interests.add((String) row.get("interest")));
        } catch (Exception ignored) {}

        // Swipe stats
        long likes      = qLong(swipeJdbc, "SELECT COUNT(*) FROM swipes WHERE from_user_id = ? AND action = 'LIKE'", userId);
        long dislikes   = qLong(swipeJdbc, "SELECT COUNT(*) FROM swipes WHERE from_user_id = ? AND action = 'DISLIKE'", userId);
        long superLikes = qLong(swipeJdbc, "SELECT COUNT(*) FROM swipes WHERE from_user_id = ? AND action = 'SUPER_LIKE'", userId);

        // Match stats
        long matchCount = qLong(matchJdbc,
                "SELECT COUNT(*) FROM matches WHERE (user1_id = ? OR user2_id = ?) AND is_active = true",
                userId, userId);

        // Mood stats
        long moodPosts    = qLong(moodJdbc, "SELECT COUNT(*) FROM mood_status WHERE user_id = ?", userId);
        long moodLikes    = qLong(moodJdbc, "SELECT COALESCE(SUM(like_count),0)    FROM mood_status WHERE user_id = ?", userId);
        long moodDislikes = qLong(moodJdbc, "SELECT COALESCE(SUM(dislike_count),0) FROM mood_status WHERE user_id = ?", userId);
        long moodComments = qLong(moodJdbc, "SELECT COALESCE(SUM(comment_count),0) FROM mood_status WHERE user_id = ?", userId);

        // Points wallet balance
        double pointsBalance = qDouble(subJdbc, "SELECT balance FROM points_wallets WHERE user_id = ?", userId);

        return UserDetail.builder()
                .userId(userId)
                .name((String) profile.get("name"))
                .age((Integer) profile.get("age"))
                .gender(Objects.toString(profile.get("gender"), null))
                .city((String) profile.get("city"))
                .bio((String) profile.get("bio"))
                .subscriptionType(Objects.toString(profile.get("subscription_type"), "FREE"))
                .photos(photos)
                .interests(interests)
                .isVerified(Boolean.TRUE.equals(profile.get("is_verified")))
                .mobile(mobile)
                .registeredAt(registeredAt)
                .totalLikes(likes).totalDislikes(dislikes).totalSuperLikes(superLikes)
                .totalMatches(matchCount)
                .totalMoodPosts(moodPosts)
                .moodLikesReceived(moodLikes)
                .moodDislikesReceived(moodDislikes)
                .moodCommentsReceived(moodComments)
                .pointsBalance(pointsBalance)
                .build();
    }

    // ── User Posts (Moods) ───────────────────────────────────────────────────────

    public List<MoodPostSummary> getUserPosts(String userId, String sortBy, int limit) {
        String order = switch (sortBy) {
            case "likes"    -> "like_count DESC";
            case "dislikes" -> "dislike_count DESC";
            case "comments" -> "comment_count DESC";
            default         -> "created_at DESC";
        };

        return moodJdbc.query(
                "SELECT id, mood_type, description, location_name, like_count, dislike_count, comment_count, created_at, is_active " +
                "FROM mood_status WHERE user_id = ? ORDER BY " + order + " LIMIT ?",
                new Object[]{userId, limit},
                (rs, rn) -> MoodPostSummary.builder()
                        .id(rs.getString("id"))
                        .moodType(rs.getString("mood_type"))
                        .description(rs.getString("description"))
                        .locationName(rs.getString("location_name"))
                        .likeCount(rs.getInt("like_count"))
                        .dislikeCount(rs.getInt("dislike_count"))
                        .commentCount(rs.getInt("comment_count"))
                        .createdAt(Objects.toString(rs.getObject("created_at"), ""))
                        .isActive(rs.getBoolean("is_active"))
                        .build());
    }

    // ── User Swipes ──────────────────────────────────────────────────────────────

    public List<SwipeActivity> getUserSwipes(String userId, String action, int limit) {
        List<Object> params = new ArrayList<>();
        params.add(userId);
        String sql = "SELECT to_user_id, action, created_at FROM swipes WHERE from_user_id = ?";

        if (action != null && !action.isBlank() && !"ALL".equals(action)) {
            sql += " AND action = ?";
            params.add(action);
        }
        sql += " ORDER BY created_at DESC LIMIT ?";
        params.add(limit);

        List<SwipeActivity> swipes = swipeJdbc.query(sql, params.toArray(), (rs, rn) -> SwipeActivity.builder()
                .toUserId(rs.getString("to_user_id"))
                .action(rs.getString("action"))
                .createdAt(Objects.toString(rs.getObject("created_at"), ""))
                .build());

        Set<String> ids = swipes.stream().map(SwipeActivity::getToUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = batchFetchNames(ids);
        swipes.forEach(s -> s.setToUserName(names.getOrDefault(s.getToUserId(), s.getToUserId())));
        return swipes;
    }

    // ── User Post Interactions ───────────────────────────────────────────────────

    public List<PostInteraction> getUserPostInteractions(String userId, int limit) {
        String sql =
            "SELECT mood_id, 'LIKE' AS type, NULL AS comment, created_at, " +
            "       (SELECT user_id FROM mood_status WHERE id::text = ml.mood_id LIMIT 1) AS owner_id, " +
            "       (SELECT description FROM mood_status WHERE id::text = ml.mood_id LIMIT 1) AS mood_desc " +
            "FROM mood_likes ml WHERE user_id = ? " +
            "UNION ALL " +
            "SELECT mood_id, 'DISLIKE' AS type, NULL AS comment, created_at, " +
            "       (SELECT user_id FROM mood_status WHERE id::text = md.mood_id LIMIT 1) AS owner_id, " +
            "       (SELECT description FROM mood_status WHERE id::text = md.mood_id LIMIT 1) AS mood_desc " +
            "FROM mood_dislikes md WHERE user_id = ? " +
            "UNION ALL " +
            "SELECT mood_id, 'COMMENT' AS type, comment, created_at, " +
            "       (SELECT user_id FROM mood_status WHERE id::text = mc.mood_id LIMIT 1) AS owner_id, " +
            "       (SELECT description FROM mood_status WHERE id::text = mc.mood_id LIMIT 1) AS mood_desc " +
            "FROM mood_comments mc WHERE user_id = ? " +
            "ORDER BY created_at DESC LIMIT ?";

        List<PostInteraction> list = moodJdbc.query(sql, new Object[]{userId, userId, userId, limit},
                (rs, rn) -> PostInteraction.builder()
                        .moodId(rs.getString("mood_id"))
                        .type(rs.getString("type"))
                        .comment(rs.getString("comment"))
                        .createdAt(Objects.toString(rs.getObject("created_at"), ""))
                        .moodOwnerUserId(rs.getString("owner_id"))
                        .moodDescription(rs.getString("mood_desc"))
                        .build());

        Set<String> ids = list.stream().map(PostInteraction::getMoodOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = batchFetchNames(ids);
        list.forEach(p -> p.setMoodOwnerName(names.getOrDefault(p.getMoodOwnerUserId(), p.getMoodOwnerUserId())));
        return list;
    }

    // ── User Matches ─────────────────────────────────────────────────────────────

    public List<MatchInfo> getUserMatches(String userId, int limit) {
        List<String> cols = Collections.emptyList();
        try {
            cols = matchJdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'matches' AND table_schema = current_schema()",
                    String.class);
        } catch (Exception e) {
            log.warn("Could not read information_schema for matches: {}", e.getMessage());
        }

        String u1Col  = cols.contains("user1_id")   ? "user1_id"   : cols.contains("user1id")   ? "user1id"   : "\"user1Id\"";
        String u2Col  = cols.contains("user2_id")   ? "user2_id"   : cols.contains("user2id")   ? "user2id"   : "\"user2Id\"";
        String atCol  = cols.contains("matched_at") ? "matched_at" : cols.contains("matchedat") ? "matchedat" : "\"matchedAt\"";
        String acCol  = cols.contains("is_active")  ? "is_active"  : "\"isActive\"";

        String sql = "SELECT id, " + u1Col + " AS u1, " + u2Col + " AS u2, "
                   + atCol + " AS mat, " + acCol + " AS active "
                   + "FROM matches WHERE (" + u1Col + " = ? OR " + u2Col + " = ?) "
                   + "ORDER BY " + atCol + " DESC LIMIT ?";

        try {
            List<MatchInfo> matches = matchJdbc.query(sql, new Object[]{userId, userId, limit}, (rs, rn) -> {
                String u1 = rs.getString("u1");
                String u2 = rs.getString("u2");
                return MatchInfo.builder()
                        .matchId(rs.getString("id"))
                        .otherUserId(userId.equals(u1) ? u2 : u1)
                        .matchedAt(Objects.toString(rs.getObject("mat"), ""))
                        .isActive(rs.getBoolean("active"))
                        .build();
            });
            Set<String> ids = matches.stream().map(MatchInfo::getOtherUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, String> names = batchFetchNames(ids);
            matches.forEach(m -> m.setOtherUserName(names.getOrDefault(m.getOtherUserId(), m.getOtherUserId())));
            return matches;
        } catch (Exception e) {
            log.warn("getUserMatches query failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── User Chats (via chat-service internal API) ────────────────────────────────

    public List<ChatSummary> getUserChats(String userId) {
        try {
            String url = chatServiceUrl + "/api/chats/internal/admin/user/" + userId + "/conversations";
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (resp.getBody() == null) return List.of();

            List<Map<String, Object>> conversations = resp.getBody();
            Set<String> otherIds = conversations.stream()
                    .map(c -> {
                        String u1 = (String) c.get("user1Id");
                        String u2 = (String) c.get("user2Id");
                        return userId.equals(u1) ? u2 : u1;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, String> nameMap = batchFetchNames(otherIds);

            return conversations.stream().map(c -> {
                String u1 = (String) c.get("user1Id");
                String u2 = (String) c.get("user2Id");
                String otherId = userId.equals(u1) ? u2 : u1;
                return ChatSummary.builder()
                        .conversationId((String) c.get("id"))
                        .otherUserId(otherId)
                        .otherUserName(nameMap.getOrDefault(otherId, otherId))
                        .lastMessage((String) c.get("lastMessage"))
                        .lastMessageType((String) c.get("lastMessageType"))
                        .lastMessageAt(Objects.toString(c.get("lastMessageAt"), ""))
                        .build();
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch chats for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ── Chat Messages (for admin conversation view) ───────────────────────────

    public List<ChatMessage> getChatMessages(String conversationId, int limit) {
        try {
            String url = chatServiceUrl + "/api/chats/internal/admin/conversations/" + conversationId + "/messages?limit=" + limit;
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            if (resp.getBody() == null) return List.of();

            Set<String> senderIds = resp.getBody().stream()
                    .map(m -> (String) m.get("senderId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<String, String> nameMap = batchFetchNames(senderIds);

            return resp.getBody().stream().map(m -> {
                String senderId = (String) m.get("senderId");
                return ChatMessage.builder()
                        .id((String) m.get("id"))
                        .senderId(senderId)
                        .senderName(nameMap.getOrDefault(senderId, senderId))
                        .type(Objects.toString(m.get("type"), "TEXT"))
                        .text((String) m.get("text"))
                        .mediaUrl((String) m.get("mediaUrl"))
                        .sentAt(Objects.toString(m.get("sentAt"), ""))
                        .seen(Boolean.TRUE.equals(m.get("seen")))
                        .build();
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch messages for conversation {}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    // ── Post Engagements — who interacted with THIS user's posts ─────────────────

    public List<PostEngagement> getUserPostEngagements(String userId, int limit) {
        String sql =
            "SELECT ms.id::text AS post_id, ms.mood_type, ms.description, " +
            "       ml.user_id AS interactor_id, 'LIKE' AS type, NULL::text AS comment, ml.created_at " +
            "FROM mood_status ms JOIN mood_likes ml ON ml.mood_id = ms.id::text " +
            "WHERE ms.user_id = ? " +
            "UNION ALL " +
            "SELECT ms.id::text, ms.mood_type, ms.description, " +
            "       md.user_id, 'DISLIKE', NULL::text, md.created_at " +
            "FROM mood_status ms JOIN mood_dislikes md ON md.mood_id = ms.id::text " +
            "WHERE ms.user_id = ? " +
            "UNION ALL " +
            "SELECT ms.id::text, ms.mood_type, ms.description, " +
            "       mc.user_id, 'COMMENT', mc.comment, mc.created_at " +
            "FROM mood_status ms JOIN mood_comments mc ON mc.mood_id = ms.id::text " +
            "WHERE ms.user_id = ? " +
            "ORDER BY created_at DESC LIMIT ?";

        List<PostEngagement> list = moodJdbc.query(sql, new Object[]{userId, userId, userId, limit},
                (rs, rn) -> PostEngagement.builder()
                        .postId(rs.getString("post_id"))
                        .moodType(rs.getString("mood_type"))
                        .postDescription(rs.getString("description"))
                        .interactorUserId(rs.getString("interactor_id"))
                        .type(rs.getString("type"))
                        .comment(rs.getString("comment"))
                        .createdAt(Objects.toString(rs.getObject("created_at"), ""))
                        .build());

        Set<String> ids = list.stream().map(PostEngagement::getInteractorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = batchFetchNames(ids);
        list.forEach(e -> e.setInteractorName(names.getOrDefault(e.getInteractorUserId(), e.getInteractorUserId())));
        return list;
    }

    // ── Reward Points — delegates to subscription-service (avoids read-only JDBC) ──

    @SuppressWarnings("unchecked")
    public Map<String, Object> giveRewardPoints(String userId, int amount, String reason) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be > 0");
        String url = subscriptionServiceUrl + "/api/subscriptions/admin/bonus-points";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "amount", (double) amount,
                "reason", reason != null && !reason.isBlank() ? reason : "Admin reward");
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(body),
                    new ParameterizedTypeReference<>() {});
            return resp.getBody() != null ? resp.getBody()
                    : Map.of("success", false, "error", "No response from subscription-service");
        } catch (Exception e) {
            throw new RuntimeException("Failed to give reward points: " + e.getMessage());
        }
    }

    // ── Ad Tracking ──────────────────────────────────────────────────────────────

    public void trackImpression(String userId, AdImpressionRequest req) {
        adRepo.save(AdImpression.builder()
                .userId(userId)
                .adType(req.getAdType())
                .watchTimeSeconds(req.getWatchTimeSeconds())
                .region(req.getRegion() != null ? req.getRegion().toUpperCase() : "IN")
                .build());
    }

    public AdStats getAdStats() {
        long total    = adRepo.count();
        long today    = adRepo.countSince(LocalDateTime.now().toLocalDate().atStartOfDay());
        double avg    = adRepo.avgWatchTime();
        long totalSec = adRepo.totalWatchTimeSeconds();

        List<RegionStat> regions = new ArrayList<>();
        for (Object[] row : adRepo.countByRegion()) {
            regions.add(RegionStat.builder()
                    .region(row[0] != null ? row[0].toString() : "OTHER")
                    .impressions(toLong(row[1]))
                    .build());
        }

        return AdStats.builder()
                .totalImpressions(total).impressionsToday(today)
                .avgWatchTimeSeconds(avg).totalWatchTimeHours(totalSec / 3600)
                .byRegion(regions)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private long qLong(JdbcTemplate jdbc, String sql, Object... params) {
        try {
            Long val = params.length == 0
                    ? jdbc.queryForObject(sql, Long.class)
                    : jdbc.queryForObject(sql, Long.class, params);
            return val != null ? val : 0L;
        } catch (Exception e) {
            log.debug("Query returned 0 ({}): {}", e.getMessage(), sql);
            return 0L;
        }
    }

    private double qDouble(JdbcTemplate jdbc, String sql, Object... params) {
        try {
            Double val = params.length == 0
                    ? jdbc.queryForObject(sql, Double.class)
                    : jdbc.queryForObject(sql, Double.class, params);
            return val != null ? val : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private Map<String, String> batchFetchNames(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        try {
            String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = "SELECT id::text, name FROM user_profiles WHERE id::text IN (" + placeholders + ")";
            Map<String, String> nameMap = new HashMap<>();
            userJdbc.queryForList(sql, userIds.toArray())
                    .forEach(row -> nameMap.put(String.valueOf(row.get("id")), String.valueOf(row.get("name"))));
            return nameMap;
        } catch (Exception e) {
            log.warn("batchFetchNames failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
