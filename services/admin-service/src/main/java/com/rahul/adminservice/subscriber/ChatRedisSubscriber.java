package com.rahul.adminservice.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.adminservice.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRedisSubscriber implements MessageListener {

    private final ChatStreamService chatStreamService;
    private final ObjectMapper      objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(json, Map.class);
            String conversationId = (String) msg.get("conversationId");
            if (conversationId != null) {
                chatStreamService.broadcast(conversationId, msg);
            }
        } catch (Exception e) {
            log.debug("Could not parse Redis chat message: {}", e.getMessage());
        }
    }
}
