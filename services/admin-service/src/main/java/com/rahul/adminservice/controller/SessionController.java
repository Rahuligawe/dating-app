package com.rahul.adminservice.controller;

import com.rahul.adminservice.entity.AppSession;
import com.rahul.adminservice.repository.AppSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Called by the Android app to track how long the user keeps the app open.
 * POST /api/sessions/start      → call when app enters foreground
 * POST /api/sessions/end/{id}   → call when app goes to background / closes
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class SessionController {

    private final AppSessionRepository sessionRepo;

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startSession(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) Map<String, String> body) {

        AppSession session = AppSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sessionStart(LocalDateTime.now())
                .appVersion(body != null ? body.getOrDefault("appVersion", null) : null)
                .build();
        sessionRepo.save(session);
        return ResponseEntity.ok(Map.of("sessionId", session.getId()));
    }

    @PostMapping("/end/{sessionId}")
    public ResponseEntity<Void> endSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId) {

        sessionRepo.findById(sessionId).ifPresent(s -> {
            if (userId.equals(s.getUserId()) && s.getSessionEnd() == null) {
                s.setSessionEnd(LocalDateTime.now());
                s.setDurationSeconds(
                        (int) ChronoUnit.SECONDS.between(s.getSessionStart(), s.getSessionEnd()));
                sessionRepo.save(s);
            }
        });
        return ResponseEntity.ok().build();
    }

    // Periodic heartbeat — keeps the session "alive" even if end is never called
    @PostMapping("/heartbeat/{sessionId}")
    public ResponseEntity<Void> heartbeat(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId) {
        // Extend the session end to now (so if app crashes, last heartbeat = last known time)
        sessionRepo.findById(sessionId).ifPresent(s -> {
            if (userId.equals(s.getUserId())) {
                LocalDateTime now = LocalDateTime.now();
                s.setSessionEnd(now);
                s.setDurationSeconds(
                        (int) ChronoUnit.SECONDS.between(s.getSessionStart(), now));
                sessionRepo.save(s);
            }
        });
        return ResponseEntity.ok().build();
    }
}
