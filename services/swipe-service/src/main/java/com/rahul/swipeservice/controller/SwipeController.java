package com.rahul.swipeservice.controller;

import com.rahul.swipeservice.dto.SwipedUserDto;
import com.rahul.swipeservice.entity.Swipe.SwipeAction;
import com.rahul.swipeservice.service.SwipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService swipeService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> swipe(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("targetUserId") String targetUserId,
            @RequestParam("action") SwipeAction action) {
        return ResponseEntity.ok(
                swipeService.swipe(userId, targetUserId, action));
    }

    @GetMapping("/liked")
    public ResponseEntity<List<Map<String, Object>>> getLikedByMe(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getLikedByMe(userId));
    }

    @GetMapping("/disliked")
    public ResponseEntity<List<Map<String, Object>>> getDislikedByMe(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getDislikedByMe(userId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingRequests(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getPendingRequests(userId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "swipe-service"));
    }

    // Jinhe maine RIGHT swipe kiya (Likes tab)
    @GetMapping("/my-likes")
    public ResponseEntity<List<SwipedUserDto>> getMyLikes(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getMyLikes(userId));
    }

    // Jinhe maine LEFT swipe kiya (Rejected tab)
    @GetMapping("/my-rejected")
    public ResponseEntity<List<SwipedUserDto>> getMyRejected(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getMyRejected(userId));
    }

    // Jinne mujhe like kiya — PAID feature
    @GetMapping("/liked-me")
    public ResponseEntity<List<SwipedUserDto>> getLikedMe(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(swipeService.getLikedMe(userId));
    }

    // Sirf count — FREE users ke liye
    @GetMapping("/liked-me/count")
    public ResponseEntity<Map<String, Long>> getLikedMeCount(
            @RequestHeader("X-User-Id") String userId) {
        long count = swipeService.getLikedMeCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // Total swipes count (profile screen ke liye)
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getSwipeCount(
            @RequestHeader("X-User-Id") String userId) {
        long count = swipeService.getTotalSwipeCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{userId}/likes-count")
    public ResponseEntity<Map<String, Long>> getLikesCount(@PathVariable String userId) {
        return ResponseEntity.ok(swipeService.getUserLikesCount(userId));
    }

    /**
     * Internal endpoint — used by notification-service when mood is posted.
     * Returns list of userIds that the given userId has LIKED or SUPER_LIKED.
     * No auth required (internal service-to-service call only).
     */
    @GetMapping("/internal/liked-user-ids/{userId}")
    public ResponseEntity<List<String>> getLikedUserIds(
            @PathVariable String userId) {
        return ResponseEntity.ok(swipeService.getLikedUserIds(userId));
    }
}