package com.rahul.notificationservice.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.notificationservice.service.NotificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private static final String GROUP = "notification-service";

    private final NotificationService    notificationService;
    private final StringRedisTemplate    stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper           objectMapper;
    private final RestTemplate           restTemplate;

    @Value("${swipe-service.url:http://localhost:8083}")
    private String swipeServiceUrl;

    @Value("${match-service.url:http://localhost:8085}")
    private String matchServiceUrl;

    @PostConstruct
    public void initGroups() {
        initGroup("stream:notification.send");
        initGroup("stream:location.nearby");
        initGroup("stream:mood.posted");
    }

    private void initGroup(String stream) {
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stream))) {
                stringRedisTemplate.opsForStream().add(stream, Map.of("init", "1"));
            }
            stringRedisTemplate.opsForStream().createGroup(stream, ReadOffset.latest(), GROUP);
            log.info("Consumer group '{}' created for stream {}", GROUP, stream);
        } catch (Exception e) {
            log.debug("Consumer group '{}' for {} already exists", GROUP, stream);
        }
    }

    // ─── notification.send ───────────────────────────────────────────────────

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> notificationSendContainer() {
        var container = buildContainer(connectionFactory);
        container.receive(
                Consumer.from(GROUP, "notif-1"),
                StreamOffset.create("stream:notification.send", ReadOffset.lastConsumed()),
                message -> {
                    try {
                        Map<String, Object> event = objectMapper.readValue(
                                message.getValue().get("payload"), new TypeReference<>() {});

                        String userId = str(event, "userId");
                        String type   = str(event, "type");
                        String title  = str(event, "title");
                        String body   = str(event, "body");

                        Map<String, String> data = new HashMap<>();
                        Object rawData = event.get("data");
                        if (rawData instanceof Map<?, ?> dataMap) {
                            dataMap.forEach((k, v) -> data.put(k.toString(), v.toString()));
                        }

                        log.info("Sending push | user: {} | type: {}", userId, type);
                        notificationService.sendPushNotification(userId, title, body, type, data);
                        stringRedisTemplate.opsForStream()
                                .acknowledge("stream:notification.send", GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process notification.send: {}", e.getMessage());
                    }
                }
        );
        container.start();
        return container;
    }

    // ─── location.nearby ────────────────────────────────────────────────────

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> locationNearbyContainer() {
        var container = buildContainer(connectionFactory);
        container.receive(
                Consumer.from(GROUP, "nearby-1"),
                StreamOffset.create("stream:location.nearby", ReadOffset.lastConsumed()),
                message -> {
                    try {
                        Map<String, Object> event = objectMapper.readValue(
                                message.getValue().get("payload"), new TypeReference<>() {});

                        String userId       = str(event, "userId");
                        String nearbyUserId = str(event, "nearbyUserId");
                        Object dist         = event.get("distanceKm");

                        notificationService.sendPushNotification(
                                userId,
                                "Someone is nearby! 📍",
                                "A match is just " + dist + " km away from you",
                                "NEARBY_ALERT",
                                Map.of("nearbyUserId", nearbyUserId,
                                        "distanceKm", dist != null ? dist.toString() : "")
                        );
                        stringRedisTemplate.opsForStream()
                                .acknowledge("stream:location.nearby", GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process location.nearby: {}", e.getMessage());
                    }
                }
        );
        container.start();
        return container;
    }

    // ─── mood.posted ─────────────────────────────────────────────────────────

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> moodPostedContainer() {
        var container = buildContainer(connectionFactory);
        container.receive(
                Consumer.from(GROUP, "mood-1"),
                StreamOffset.create("stream:mood.posted", ReadOffset.lastConsumed()),
                message -> {
                    try {
                        Map<String, Object> event = objectMapper.readValue(
                                message.getValue().get("payload"), new TypeReference<>() {});

                        String userId      = str(event, "userId");
                        String moodType    = str(event, "moodType");
                        String description = str(event, "description");

                        if (userId.isBlank()) {
                            log.warn("mood.posted event with blank userId — skipping");
                            return;
                        }

                        // Notify all MATCHES (mutual likes) — not just one-sided likers
                        String url = matchServiceUrl + "/api/matches/internal/match-user-ids/" + userId;
                        String[] likedUserIds = restTemplate.getForObject(url, String[].class);

                        if (likedUserIds == null || likedUserIds.length == 0) return;

                        String label = moodType.replace('_', ' ').toLowerCase();
                        String body  = (description == null || description.isBlank())
                                ? "Wants to " + label + " with someone! 😊"
                                : description;

                        for (String targetUserId : likedUserIds) {
                            notificationService.sendPushNotification(
                                    targetUserId,
                                    "New Mood Update 😊", body, "MOOD_UPDATE",
                                    Map.of("moodUserId", userId, "moodType", moodType)
                            );
                        }

                        stringRedisTemplate.opsForStream()
                                .acknowledge("stream:mood.posted", GROUP, message.getId());
                    } catch (Exception e) {
                        log.error("Failed to process mood.posted: {}", e.getMessage());
                    }
                }
        );
        container.start();
        return container;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> buildContainer(
            RedisConnectionFactory factory) {
        var options = StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofMillis(200))
                .build();
        return StreamMessageListenerContainer.create(factory, options);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
