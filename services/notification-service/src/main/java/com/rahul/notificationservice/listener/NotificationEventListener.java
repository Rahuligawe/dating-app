package com.rahul.notificationservice.listener;

import com.rahul.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final RestTemplate        restTemplate;

    @Value("${swipe-service.url:http://localhost:8083}")
    private String swipeServiceUrl;

    // ─── Main notification topic (all services send here) ────

    @KafkaListener(topics = "notification.send",
            groupId = "notification-service")
    public void handleNotification(Map<String, Object> event) {
        String userId = getString(event, "userId");
        String type   = getString(event, "type");
        String title  = getString(event, "title");
        String body   = getString(event, "body");

        // Extract extra data map if present
        Map<String, String> data = new HashMap<>();
        Object rawData = event.get("data");
        if (rawData instanceof Map<?, ?> dataMap) {
            dataMap.forEach((k, v) ->
                    data.put(k.toString(), v.toString()));
        }

        log.info("Sending notification | user: {} | type: {}",
                userId, type);

        notificationService.sendPushNotification(
                userId, title, body, type, data);
    }

    // ─── Nearby alert topic (location-service sends here) ────

    @KafkaListener(topics = "location.nearby",
            groupId = "notification-service")
    public void handleNearbyAlert(Map<String, Object> event) {
        String userId       = getString(event, "userId");
        String nearbyUserId = getString(event, "nearbyUserId");
        Object dist         = event.get("distanceKm");

        notificationService.sendPushNotification(
                userId,
                "Someone is nearby! 📍",
                "A match is just " + dist + " km away from you",
                "NEARBY_ALERT",
                Map.of(
                        "nearbyUserId", nearbyUserId,
                        "distanceKm",   dist.toString()
                )
        );
    }

    // ─── Mood Posted topic — notify users the poster liked ───

    /**
     * Rule: When User X posts a mood → send notification to every user
     * that User X has LIKED (or SUPER_LIKED) via swipe.
     * Mutual matches are automatically included (they liked each other).
     * Users who only liked X but X did NOT like back → NO notification.
     */
    @KafkaListener(topics = "mood.posted",
            groupId = "notification-service")
    public void handleMoodPosted(Map<String, Object> event) {
        String userId      = getString(event, "userId");
        String moodType    = getString(event, "moodType");
        String description = getString(event, "description");

        if (userId.isBlank()) {
            log.warn("mood.posted event received with blank userId — skipping");
            return;
        }

        try {
            // Fetch the list of userIds that this user has liked
            String url = swipeServiceUrl
                    + "/api/swipes/internal/liked-user-ids/" + userId;
            String[] likedUserIds = restTemplate.getForObject(url, String[].class);

            if (likedUserIds == null || likedUserIds.length == 0) {
                log.debug("No liked users to notify for moodPosted | userId={}", userId);
                return;
            }

            // Build notification body
            String label = moodType.replace('_', ' ').toLowerCase();
            String body  = (description == null || description.isBlank())
                    ? "Wants to " + label + " with someone! 😊"
                    : description;

            for (String targetUserId : likedUserIds) {
                notificationService.sendPushNotification(
                        targetUserId,
                        "New Mood Update 😊",
                        body,
                        "MOOD_UPDATE",
                        Map.of("moodUserId", userId, "moodType", moodType)
                );
            }

            log.info("Mood notifications sent | from={} | recipients={}",
                    userId, likedUserIds.length);

        } catch (Exception e) {
            log.error("Failed to send mood notifications for userId={}: {}",
                    userId, e.getMessage());
        }
    }

    // ─── Helper ───────────────────────────────────────────────

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}