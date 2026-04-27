package com.rahul.adminservice.controller;

import com.rahul.adminservice.dto.AdminDtos.*;
import com.rahul.adminservice.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminController {

    private final AdminService adminService;

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
