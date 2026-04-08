package com.rahul.chatservice.listener;

import com.rahul.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatEventListener {

    private final ChatService chatService;

    // When match is created → create conversation
    @KafkaListener(topics = "chat.create.conversation",
            groupId = "chat-service")
    public void onMatchCreated(Map<String, String> event) {
        String matchId = event.get("matchId");
        String user1Id = event.get("user1Id");
        String user2Id = event.get("user2Id");

        log.info("Creating conversation for match: {}", matchId);
        chatService.createConversation(matchId, user1Id, user2Id);
    }
}