package com.rahul.chatservice.controller;

import com.rahul.chatservice.dto.ConversationSummaryResponse;
import com.rahul.chatservice.dto.SendMessageRequest;
import com.rahul.chatservice.entity.Conversation;
import com.rahul.chatservice.entity.Message;
import com.rahul.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ─── REST Endpoints ───────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Conversation>> getConversations(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(chatService.getConversations(userId));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(
                chatService.getMessages(conversationId, userId, page, size));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<Message> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("conversationId") String conversationId,
            @RequestBody SendMessageRequest request) {
        Message msg = chatService.sendMessage(
                conversationId,
                userId,
                request.getReceiverId(),
                request.getText(),
                request.getType(),
                request.getMediaUrl(),
                request.getLocationLat(),
                request.getLocationLong()
        );
        return ResponseEntity.ok(msg);
    }

    @PostMapping("/{conversationId}/seen")
    public ResponseEntity<Void> markAsSeen(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("conversationId") String conversationId) {
        chatService.markAsSeen(conversationId, userId);
        return ResponseEntity.ok().build();
    }

    // Angel ki device pe message aaya → immediately call karo (AuraLinkApplication level)
    // Rahul ko double grey tick milega bina Angel ke chat khole
    @PostMapping("/{conversationId}/delivered")
    public ResponseEntity<Void> markAsDelivered(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("conversationId") String conversationId) {
        chatService.markAsDelivered(conversationId, userId);
        return ResponseEntity.ok().build();
    }

    // Bulk delete: DELETE /api/chats/{conversationId}/messages
    // Body: ["msgId1", "msgId2", ...]
    // Security: only a conversation participant can delete
    @DeleteMapping("/{conversationId}/messages")
    public ResponseEntity<Void> deleteMessages(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("conversationId") String conversationId,
            @RequestBody List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        chatService.deleteMessages(conversationId, userId, messageIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/unread")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("conversationId") String conversationId) {
        long count = chatService.getUnreadCount(conversationId, userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // Called internally by match-service to get last message + unread count
    @GetMapping("/{conversationId}/summary")
    public ResponseEntity<ConversationSummaryResponse> getConversationSummary(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String conversationId) {
        return ResponseEntity.ok(chatService.getConversationSummary(conversationId, userId));
    }

    // Internal: Admin dashboard calls this to view a user's conversations
    @GetMapping("/internal/admin/user/{userId}/conversations")
    public ResponseEntity<List<Conversation>> getAdminUserConversations(
            @PathVariable String userId) {
        return ResponseEntity.ok(chatService.getConversations(userId));
    }

    // Internal: Admin dashboard calls this to view full conversation history
    @GetMapping("/internal/admin/conversations/{conversationId}/messages")
    public ResponseEntity<List<Message>> getAdminConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(chatService.getAdminMessages(conversationId, limit));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of("status", "UP", "service", "chat-service"));
    }

    // ─── WebSocket Endpoint ───────────────────────────────────

    @MessageMapping("/chat/{conversationId}")
    public void receiveWebSocketMessage(
            @DestinationVariable("conversationId") String conversationId,
            @Payload SendMessageRequest request) {
        chatService.sendMessage(
                conversationId,

                request.getSenderId(),
                request.getReceiverId(),
                request.getText(),
                request.getType(),
                request.getMediaUrl(),
                request.getLocationLat(),
                request.getLocationLong()
        );
    }
}