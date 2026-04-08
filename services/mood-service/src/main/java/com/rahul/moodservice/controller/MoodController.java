package com.rahul.moodservice.controller;

import com.rahul.moodservice.dto.MoodDtos;
import com.rahul.moodservice.dto.MoodDtos.*;
import com.rahul.moodservice.entity.*;
import com.rahul.moodservice.service.MoodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/moods")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService moodService;

    @PostMapping
    public ResponseEntity<MoodStatus> postMood(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PostMoodRequest request) {
        return ResponseEntity.ok(moodService.postMood(userId, request));
    }

    /*@GetMapping("/feed")
    public ResponseEntity<List<MoodStatus>> getMoodFeed(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                moodService.getActiveMoodFeed(userId));
    }*/

    @GetMapping("/feed")
    public ResponseEntity<List<Map<String, Object>>> getMoodFeed(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                moodService.getActiveMoodFeedWithReactions(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<MoodStatus> getMyActiveMood(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                moodService.getMyActiveMood(userId));
    }

    @GetMapping("/my/history")
    public ResponseEntity<List<MoodStatus>> getMyMoodHistory(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                moodService.getMyMoodHistory(userId));
    }

    @GetMapping("/{moodId}")
    public ResponseEntity<Map<String, Object>> getMoodById(
            @PathVariable String moodId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> mood = moodService.getMoodByIdWithReactions(moodId, userId);
        if (mood == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(mood);
    }

    @PostMapping("/{moodId}/like")
    public ResponseEntity<Map<String, Object>> likeMood(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String moodId) {
        return ResponseEntity.ok(
                moodService.likeMood(moodId, userId));
    }

    @PostMapping("/{moodId}/dislike")
    public ResponseEntity<Map<String, Object>> dislikeMood(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String moodId) {
        return ResponseEntity.ok(
                moodService.dislikeMood(moodId, userId));
    }

    @PostMapping("/{moodId}/comments")
    public ResponseEntity<MoodDtos.MoodCommentResponse> addComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String moodId,
            @Valid @RequestBody AddCommentRequest request) {
        return ResponseEntity.ok(
                moodService.addComment(moodId, userId, request)
        );
    }

    @GetMapping("/{moodId}/comments")
    public ResponseEntity<List<MoodComment>> getComments(
            @PathVariable String moodId) {
        return ResponseEntity.ok(moodService.getComments(moodId));
    }

    @PostMapping("/{moodId}/join")
    public ResponseEntity<Map<String, Object>> sendJoinRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String moodId) {
        return ResponseEntity.ok(
                moodService.sendJoinRequest(moodId, userId));
    }

    @PostMapping("/{moodId}/join/respond")
    public ResponseEntity<Map<String, String>> respondToJoin(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String moodId,
            @RequestBody RespondJoinRequest request) {
        return ResponseEntity.ok(
                moodService.respondToJoinRequest(moodId, userId, request));
    }

    @GetMapping("/{moodId}/join/pending")
    public ResponseEntity<List<MoodJoinRequest>> getPendingRequests(
            @PathVariable String moodId) {
        return ResponseEntity.ok(
                moodService.getPendingJoinRequests(moodId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "mood-service"));
    }
}