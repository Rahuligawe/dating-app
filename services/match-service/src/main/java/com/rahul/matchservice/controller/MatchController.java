package com.rahul.matchservice.controller;

import com.rahul.matchservice.dto.MatchWithProfileResponse;
import com.rahul.matchservice.entity.Match;
import com.rahul.matchservice.service.MatchService;
import com.rahul.matchservice.service.MatchServiceOld;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    public ResponseEntity<List<Match>> getMyMatches(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(matchService.getMatchesForUser(userId));
    }

    // ✅ NEW — Chats screen ke liye: match + other user profile + last message
    @GetMapping("/with-profiles")
    public ResponseEntity<List<MatchWithProfileResponse>> getMatchesWithProfiles(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(matchService.getMatchesWithProfiles(userId));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkMatch(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String otherUserId) {
        boolean matched = matchService.areUsersMatched(userId, otherUserId);
        return ResponseEntity.ok(Map.of("isMatched", matched));
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<Map<String, String>> unmatch(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String matchId) {
        matchService.unmatch(matchId, userId);
        return ResponseEntity.ok(Map.of("message", "Unmatched successfully"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "match-service"));
    }

    // [Profile Screen] Match count
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getMatchCount(
            @RequestHeader("X-User-Id") String userId) {
        long count = matchService.getMatchCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }
}