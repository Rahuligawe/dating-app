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
            };
            conv.setLastMessage(preview);
            conv.setLastMessageType(message.getType().name());
            conv.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conv);
        });

        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, message);

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

    // ─── Unread Count ─────────────────────────────────────────

    public long getUnreadCount(String conversationId, String userId) {
        return messageRepository
                .countByConversationIdAndSeenFalseAndSenderIdNot(conversationId, userId);
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