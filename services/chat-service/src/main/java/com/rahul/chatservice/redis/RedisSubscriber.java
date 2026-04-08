package com.rahul.chatservice.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.chatservice.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void receiveMessage(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            if (node.has("conversationId")) {
                // Chat message — deliver to /topic/chat/{conversationId}
                Message message = objectMapper.treeToValue(node, Message.class);
                messagingTemplate.convertAndSend(
                    "/topic/chat/" + message.getConversationId(), message
                );
                log.debug("Redis → chat delivered to /topic/chat/{}", message.getConversationId());

            } else if (node.has("online") && node.has("userId")) {
                // Presence update — deliver to /topic/status/{userId}
                String userId = node.get("userId").asText();
                messagingTemplate.convertAndSend("/topic/status/" + userId, json);
                log.debug("Redis → status delivered to /topic/status/{}", userId);
            }

        } catch (Exception e) {
            log.warn("Redis subscriber failed: {}", e.getMessage());
        }
    }
}