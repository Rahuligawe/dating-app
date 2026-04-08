package com.rahul.notificationservice.controller;

import com.rahul.notificationservice.entity.NotificationLog;
import com.rahul.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/register-token")
    public ResponseEntity<Map<String, String>> registerToken(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        notificationService.registerToken(
                userId,
                body.get("token"),
                body.get("platform")
        );
        return ResponseEntity.ok(
                Map.of("message", "Token registered successfully"));
    }

    @GetMapping
    public ResponseEntity<List<NotificationLog>> getNotifications(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                notificationService.getNotifications(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(Map.of("unreadCount",
                notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestHeader("X-User-Id") String userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(
                Map.of("message", "All notifications marked as read"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "notification-service"));
    }
}