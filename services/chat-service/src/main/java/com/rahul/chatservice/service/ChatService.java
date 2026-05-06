package com.rahul.chatservice.service;

import com.rahul.chatservice.redis.RedisPublisher;
import com.rahul.chatservice.stream.StreamPublisher;
import com.rahul.chatservice.dto.ConversationSummaryResponse;
import com.rahul.chatservice.entity.Conversation;
import com.rahul.chatservice.entity.Message;
import com.rahul.chatservice.entity.Message.MessageType;
import com.rahul.chatservice.repository.ConversationRepository;
import com.rahul.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamPublisher streamPublisher;
    private final RedisPublisher redisPublisher;

    // ─── Create Conversation ──────────────────────────────────

    public void createConversation(String matchId, String user1Id, String user2Id) {
        if (!conversationRepository.existsById(matchId)) {
            conversationRepository.save(Conversation.builder()
                    .id(matchId).user1Id(user1Id).user2Id(user2Id)
                    .createdAt(LocalDateTime.now()).build());
            log.info("Conversation created for match: {}", matchId);
        }
    }

    // ─── Send Message ─────────────────────────────────────────

    public Message sendMessage(String conversationId, String senderId,
                               String receiverId, String text, MessageType type,
                               String mediaUrl, Double lat, Double lng) {

        Message message = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(receiverId)
                .type(type != null ? type : MessageType.TEXT)
                .text(text)
                .mediaUrl(mediaUrl)
                .locationLat(lat)
                .locationLong(lng)
                .seen(false)
                // [Issue 3 - Tick Logic] delivered false by default — single tick
                // will become true when receiver calls getMessages
                .delivered(false)
                .sentAt(LocalDateTime.now())
                .build());

        conversationRepository.findById(conversationId).ifPresent(conv -> {
            String preview = switch (message.getType()) {
                case TEXT     -> text;
                case IMAGE    -> "📷 Photo";
                case VOICE    -> "🎤 Voice message";
                case VIDEO    -> "🎥 Video";
                case LOCATION -> "📍 Location";
                case GIF      -> "GIF";
                case FILE     -> "📄 Document";
            };
            conv.setLastMessage(preview);
            conv.setLastMessageType(message.getType().name());
            conv.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conv);
        });

        // Broadcast message to chat topic (both users in conversation)
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, message);

        // Inbox publish — receiver ki device pe subscribe hai (AuraLinkApplication level)
        // Jaise hi message aayega, receiver immediately markAsDelivered call karega
        // Rahul ko bina Angel ke chat khole double grey tick mil jaayega
        messagingTemplate.convertAndSend("/topic/inbox/" + receiverId,
                Map.of("conversationId", conversationId, "messageId", message.getId()));

        try {
            redisPublisher.publish("chat", message);
        } catch (Exception redisEx) {
            log.warn("Redis publish failed (non-fatal): {}", redisEx.getMessage());
        }
        Map<String, Object> notifPayload = new HashMap<>();
        notifPayload.put("userId", receiverId);
        notifPayload.put("type", "NEW_MESSAGE");
        notifPayload.put("title", "New Message 💬");
        notifPayload.put("body", text != null ? text : "Sent you a message");
        notifPayload.put("data", Map.of("conversationId", conversationId, "senderId", senderId));
        streamPublisher.publish("notification.send", notifPayload);
        return message;
    }

    // ─── Get Messages ─────────────────────────────────────────

    // [Issue 3 - Tick Logic] jab receiver getMessages call kare
    // un messages ko delivered=true mark karo jinke receiver = userId
    // phir sender ko WebSocket se notify karo — double tick dikhane ke liye
    public List<Message> getMessages(String conversationId, String requestingUserId,
                                     int page, int size) {
        List<Message> messages = messageRepository.findByConversationIdOrderBySentAtDesc(
                conversationId, PageRequest.of(page, size));

        // Mark undelivered messages as delivered
        List<Message> toDeliver = messages.stream()
                .filter(m -> requestingUserId.equals(m.getReceiverId())
                        && Boolean.FALSE.equals(m.getDelivered()))
                .toList();

        if (!toDeliver.isEmpty()) {
            toDeliver.forEach(m -> {
                m.setDelivered(true);
                m.setDeliveredAt(LocalDateTime.now());
            });
            messageRepository.saveAll(toDeliver);

            // Notify sender — double tick dikhao
            String senderId = toDeliver.get(0).getSenderId();
            messagingTemplate.convertAndSend(
                    "/topic/delivered/" + conversationId,
                    Map.of("conversationId", conversationId,
                            "deliveredTo", requestingUserId,
                            "messageIds", toDeliver.stream().map(Message::getId).toList()));
        }

        return messages;
    }

    // ─── Get Conversations ────────────────────────────────────

    public List<Conversation> getConversations(String userId) {
        return conversationRepository
                .findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId);
    }

    // ─── Mark as Seen ─────────────────────────────────────────

    public void markAsSeen(String conversationId, String userId) {
        List<Message> unseen = messageRepository.findUnseenMessages(conversationId, userId);

        unseen.forEach(m -> {
            m.setSeen(true);
            m.setSeenAt(LocalDateTime.now());
            // [Issue 3 - Tick Logic] seen implies delivered
            if (Boolean.FALSE.equals(m.getDelivered())) {
                m.setDelivered(true);
                m.setDeliveredAt(LocalDateTime.now());
            }
        });

        if (!unseen.isEmpty()) {
            messageRepository.saveAll(unseen);
            String senderId = unseen.get(0).getSenderId();
            messagingTemplate.convertAndSend(
                    "/topic/seen/" + conversationId,
                    Map.of("conversationId", conversationId, "seenBy", userId));
        }
    }

    // ─── Mark as Delivered ────────────────────────────────────
    // Angel ki device pe message aaya (WS se) → immediately call karo
    // Rahul ko double grey tick milega bina Angel ke chat khole

    public void markAsDelivered(String conversationId, String receiverId) {
        List<Message> undelivered = messageRepository
                .findUndeliveredMessages(conversationId, receiverId);

        if (undelivered.isEmpty()) return;

        undelivered.forEach(m -> {
            m.setDelivered(true);
            m.setDeliveredAt(LocalDateTime.now());
        });
        messageRepository.saveAll(undelivered);

        messagingTemplate.convertAndSend(
                "/topic/delivered/" + conversationId,
                Map.of("conversationId", conversationId,
                        "deliveredTo", receiverId,
                        "messageIds", undelivered.stream().map(Message::getId).toList()));

        log.info("Delivered {} messages in conv {} to {}", undelivered.size(), conversationId, receiverId);
    }

    // ─── Unread Count ─────────────────────────────────────────

    public long getUnreadCount(String conversationId, String userId) {
        return messageRepository
                .countByConversationIdAndSeenFalseAndSenderIdNot(conversationId, userId);
    }

    // ─── Delete Messages (bulk) ───────────────────────────────────────────────
    // Only a conversation participant can delete. Deletes from MongoDB permanently.
    public void deleteMessages(String conversationId, String requestingUserId,
                               List<String> messageIds) {
        // Security: verify requester is a participant in this conversation
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return;
        boolean isParticipant = requestingUserId.equals(conv.getUser1Id())
                || requestingUserId.equals(conv.getUser2Id());
        if (!isParticipant) {
            log.warn("User {} tried to delete messages in conversation {} but is not a participant",
                    requestingUserId, conversationId);
            return;
        }
        // Delete only the requested IDs that actually belong to this conversation
        List<Message> toDelete = messageRepository.findAllById(messageIds).stream()
                .filter(m -> conversationId.equals(m.getConversationId()))
                .toList();
        if (!toDelete.isEmpty()) {
            messageRepository.deleteAll(toDelete);
            log.info("User {} deleted {} messages from conversation {}",
                    requestingUserId, toDelete.size(), conversationId);
        }
    }

    // ─── Admin: Get Full Conversation (no side effects) ───────────────────────

    public List<Message> getAdminMessages(String conversationId, int limit) {
        return messageRepository.findByConversationIdOrderBySentAtDesc(
                conversationId, PageRequest.of(0, limit));
    }

    public ConversationSummaryResponse getConversationSummary(
            String conversationId, String userId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null) return new ConversationSummaryResponse(conversationId, null, null, 0);
        int unread = messageRepository.countUnseenByConversationIdAndNotSender(conversationId, userId);
        return new ConversationSummaryResponse(
                conv.getId(),
                conv.getLastMessage(),
                conv.getLastMessageAt() != null ? conv.getLastMessageAt().toString() : null,
                unread);
    }
}