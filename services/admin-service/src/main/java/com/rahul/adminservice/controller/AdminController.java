package com.rahul.adminservice.controller;

import com.rahul.adminservice.dto.AdminDtos.*;
import com.rahul.adminservice.service.AdminService;
import com.rahul.adminservice.service.ChatStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminController {

    private final AdminService     adminService;
    private final ChatStreamService chatStreamService;

    // ── Auth check — called at the top of every admin endpoint ────────────────

    private void requireAdmin(HttpServletRequest req) {
        String role = req.getHeader("X-User-Role");
        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("Access Denied — ADMIN role required");
        }
    }

    // ── Exception mapping ─────────────────────────────────────────────────────

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/stats")
    public ResponseEntity<DashboardStats> getStats(HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/api/admin/online-count")
    public ResponseEntity<Map<String, Long>> getOnlineCount(HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(Map.of("count", adminService.getOnlineUserCount()));
    }

    /** days=7|14|30 — returns daily chart data */
    @GetMapping("/api/admin/growth")
    public ResponseEntity<UserGrowth> getGrowth(
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserGrowth(days));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REVENUE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/revenue")
    public ResponseEntity<RevenueStats> getRevenue(HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getRevenue());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // USERS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/users")
    public ResponseEntity<List<UserSummary>> getUsers(
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "20")  int    size,
            @RequestParam(defaultValue = "ALL") String plan,
            @RequestParam(required = false)     String search,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUsers(page, size, plan, search));
    }

    @GetMapping("/api/admin/user/{userId}")
    public ResponseEntity<UserDetail> getUserDetail(
            @PathVariable String userId,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserDetail(userId));
    }

    @GetMapping("/api/admin/user/{userId}/posts")
    public ResponseEntity<List<MoodPostSummary>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "date")  String sortBy,
            @RequestParam(defaultValue = "50")    int    limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserPosts(userId, sortBy, limit));
    }

    @GetMapping("/api/admin/user/{userId}/swipes")
    public ResponseEntity<List<SwipeActivity>> getUserSwipes(
            @PathVariable String userId,
            @RequestParam(defaultValue = "ALL") String action,
            @RequestParam(defaultValue = "100") int    limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserSwipes(userId, action, limit));
    }

    @GetMapping("/api/admin/user/{userId}/post-interactions")
    public ResponseEntity<List<PostInteraction>> getUserPostInteractions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserPostInteractions(userId, limit));
    }

    @GetMapping("/api/admin/user/{userId}/matches")
    public ResponseEntity<List<MatchInfo>> getUserMatches(
            @PathVariable String userId,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserMatches(userId, limit));
    }

    @GetMapping("/api/admin/user/{userId}/chats")
    public ResponseEntity<List<ChatSummary>> getUserChats(
            @PathVariable String userId,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserChats(userId));
    }

    @GetMapping("/api/admin/user/{userId}/post-engagements")
    public ResponseEntity<List<PostEngagement>> getUserPostEngagements(
            @PathVariable String userId,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserPostEngagements(userId, limit));
    }

    // SSE stream — real-time chat updates via Redis pub/sub (no polling)
    @GetMapping(value = "/api/admin/chats/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatMessages(
            @PathVariable String conversationId,
            HttpServletRequest req) {
        requireAdmin(req);
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout
        chatStreamService.addEmitter(conversationId, emitter);
        emitter.onCompletion(() -> chatStreamService.removeEmitter(conversationId, emitter));
        emitter.onTimeout(() -> {
            chatStreamService.removeEmitter(conversationId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> chatStreamService.removeEmitter(conversationId, emitter));
        return emitter;
    }

    @GetMapping("/api/admin/chats/{conversationId}/messages")
    public ResponseEntity<List<ChatMessage>> getChatMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getChatMessages(conversationId, limit));
    }

    @PostMapping("/api/admin/user/{userId}/reward-points")
    public ResponseEntity<Map<String, Object>> giveRewardPoints(
            @PathVariable String userId,
            @RequestBody RewardPointsRequest request,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.giveRewardPoints(userId, request.getAmount(), request.getReason()));
    }

    @PostMapping("/api/admin/user/{userId}/remove-points")
    public ResponseEntity<Map<String, Object>> removePoints(
            @PathVariable String userId,
            @RequestBody RewardPointsRequest request,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.removePoints(userId, request.getAmount(), request.getReason()));
    }

    @GetMapping("/api/admin/user/by-referral/{code}")
    public ResponseEntity<UserLookup> getUserByReferralCode(
            @PathVariable String code,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserByReferralCode(code));
    }

    @PostMapping("/api/admin/user/{fromUserId}/gift-points")
    public ResponseEntity<Map<String, Object>> giftPoints(
            @PathVariable String fromUserId,
            @RequestBody GiftPointsRequest request,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.giftPoints(
                fromUserId, request.getRecipientReferralCode(), request.getAmount(), request.getReason()));
    }

    @GetMapping("/api/admin/user/{userId}/daily-stats")
    public ResponseEntity<List<DailyStat>> getDailyStats(
            @PathVariable String userId,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getDailyStats(userId, days));
    }

    @GetMapping("/api/admin/user/{userId}/who-swiped")
    public ResponseEntity<List<StatUser>> getWhoSwipedUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "ALL") String action,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getWhoSwipedUser(userId, action, limit));
    }

    @GetMapping("/api/admin/user/{userId}/session-stats")
    public ResponseEntity<List<SessionDailyStat>> getUserSessionStats(
            @PathVariable String userId,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getUserSessionStats(userId, days));
    }

    @DeleteMapping("/api/admin/user/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable String userId,
            HttpServletRequest req) {
        requireAdmin(req);
        adminService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("status", "deleted", "userId", userId));
    }

    @PostMapping("/api/admin/users")
    public ResponseEntity<UserSummary> createUser(
            @RequestBody CreateUserRequest request,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.createUser(request.getName(), request.getMobile()));
    }

    @DeleteMapping("/api/admin/comment/{commentId}")
    public ResponseEntity<Map<String, String>> deleteComment(
            @PathVariable String commentId,
            HttpServletRequest req) {
        requireAdmin(req);
        adminService.deleteComment(commentId);
        return ResponseEntity.ok(Map.of("status", "deleted", "commentId", commentId));
    }

    @GetMapping("/api/admin/sessions/top-users")
    public ResponseEntity<List<TopSessionUser>> getTopSessionUsers(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getTopSessionUsers(days, limit));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADS  (impression tracking — accessible by any valid JWT, not just ADMIN)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called from Android app after each ad impression */
    @PostMapping("/api/ads/impression")
    public ResponseEntity<Void> trackImpression(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody AdImpressionRequest request) {
        adminService.trackImpression(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/admin/ads/stats")
    public ResponseEntity<AdStats> getAdStats(HttpServletRequest req) {
        requireAdmin(req);
        return ResponseEntity.ok(adminService.getAdStats());
    }
}
