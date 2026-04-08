package com.rahul.chatservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to STOMP WebSocket connect/disconnect events.
 * When Android connects → user is online.
 * When Android disconnects (app closed/network drop) → user is offline.
 *
 * Uses the same Redis key prefix as user-service ("presence:{userId}")
 * so both services share the same online state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final StringRedisTemplate redisTemplate;

    private static final String PRESENCE_KEY  = "presence:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(5); // safety net

    // sessionId → userId mapping (in-memory, single instance)
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String userId    = sha.getFirstNativeHeader("userId");
        String sessionId = sha.getSessionId();

        if (userId == null || sessionId == null) return;

        sessionToUser.put(sessionId, userId);

        // Mark online in Redis (same key as user-service)
        redisTemplate.opsForValue().set(PRESENCE_KEY + userId, "1", PRESENCE_TTL);

        // Publish to Redis so RedisSubscriber broadcasts via WebSocket to all instances
        publishStatus(userId, true);

        log.debug("WS connect → online: {}", userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String userId    = sessionToUser.remove(sessionId);

        if (userId == null) return;

        // Mark offline in Redis
        redisTemplate.delete(PRESENCE_KEY + userId);

        // Publish offline event
        publishStatus(userId, false);

        log.debug("WS disconnect → offline: {}", userId);
    }

    private void publishStatus(String userId, boolean online) {
        try {
            String json = "{\"userId\":\"" + userId + "\",\"online\":" + online + "}";
            redisTemplate.convertAndSend("status." + userId, json);
        } catch (Exception e) {
            log.warn("Redis presence publish failed (non-fatal): {}", e.getMessage());
        }
    }
}
