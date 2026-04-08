package com.rahul.matchservice.service;

import com.rahul.matchservice.dto.ConversationSummaryDto;
import com.rahul.matchservice.dto.MatchWithProfileResponse;
import com.rahul.matchservice.dto.MatchWithProfileResponse.OtherUserDto;
import com.rahul.matchservice.entity.Match;
import com.rahul.matchservice.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient userServiceClient;
    private final WebClient chatServiceClient;

    // Constructor injection (cannot use @RequiredArgsConstructor with @Qualifier)
    public MatchService(
            MatchRepository matchRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("userServiceClient") WebClient userServiceClient,
            @Qualifier("chatServiceClient") WebClient chatServiceClient) {
        this.matchRepository    = matchRepository;
        this.kafkaTemplate      = kafkaTemplate;
        this.userServiceClient  = userServiceClient;
        this.chatServiceClient  = chatServiceClient;
    }

    // ─── Kafka: Create Match ──────────────────────────────────────────────────

    @KafkaListener(topics = "match.create", groupId = "match-service")
    @Transactional
    public void handleMatchCreate(Map<String, String> event) {
        String user1Id = event.get("user1Id");
        String user2Id = event.get("user2Id");

        if (matchRepository.existsByUser1IdAndUser2Id(user1Id, user2Id)
                || matchRepository.existsByUser1IdAndUser2Id(user2Id, user1Id)) {
            log.info("Match already exists between {} and {}", user1Id, user2Id);
            return;
        }

        Match match = matchRepository.save(Match.builder()
                .user1Id(user1Id)
                .user2Id(user2Id)
                .build());

        log.info("✅ Match created: {} <-> {} | matchId: {}", user1Id, user2Id, match.getId());

        // Notify both users
        kafkaTemplate.send("notification.send", user1Id, Map.of(
                "userId", user1Id, "type", "NEW_MATCH",
                "title", "It's a Match! 💘", "body", "You have a new match!",
                "data", Map.of("matchId", match.getId(), "matchedUserId", user2Id)
        ));
        kafkaTemplate.send("notification.send", user2Id, Map.of(
                "userId", user2Id, "type", "NEW_MATCH",
                "title", "It's a Match! 💘", "body", "You have a new match!",
                "data", Map.of("matchId", match.getId(), "matchedUserId", user1Id)
        ));

        // Tell chat-service to create a conversation
        kafkaTemplate.send("chat.create.conversation", match.getId(), Map.of(
                "matchId", match.getId(),
                "user1Id", user1Id,
                "user2Id", user2Id
        ));
    }

    // ─── REST: Basic match list ───────────────────────────────────────────────

    public List<Match> getMatchesForUser(String userId) {
        return matchRepository.findByUser1IdOrUser2Id(userId, userId);
    }

    public boolean areUsersMatched(String userId1, String userId2) {
        return matchRepository.findMatchBetween(userId1, userId2).isPresent();
    }

    @Transactional
    public void unmatch(String matchId, String requestingUserId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (!match.getUser1Id().equals(requestingUserId)
                && !match.getUser2Id().equals(requestingUserId)) {
            throw new RuntimeException("Not authorized to unmatch");
        }

        match.setIsActive(false);
        matchRepository.save(match);
        log.info("Unmatched: {}", matchId);
    }

    // ─── REST: Matches WITH other user profile + last message ─────────────────
    // Called by GET /api/matches/with-profiles
    // Used by Android Chats screen (grid view)

    public List<MatchWithProfileResponse> getMatchesWithProfiles(String userId) {
        List<Match> matches = matchRepository.findByUser1IdOrUser2Id(userId, userId);
        List<MatchWithProfileResponse> result = new ArrayList<>();

        for (Match match : matches) {
            if (Boolean.FALSE.equals(match.getIsActive())) continue;

            String otherUserId = match.getUser1Id().equals(userId)
                    ? match.getUser2Id()
                    : match.getUser1Id();

            // 1. Fetch other user's profile from user-service
            OtherUserDto otherUser = fetchUserProfile(otherUserId);

            // ✅ Profile fetch fail hua (user-service down / profile incomplete)
            // Is match ko skip karo — Android cache se purana valid data dikhayega
            if (otherUser == null) continue;

            // 2. Fetch conversation summary (lastMessage, unreadCount) from chat-service
            ConversationSummaryDto conv = fetchConversation(match.getId(), userId);

            result.add(MatchWithProfileResponse.builder()
                    .matchId(match.getId())
                    .conversationId(match.getId())   // chat-service uses matchId as conversationId
                    .matchedAt(match.getMatchedAt() != null ? match.getMatchedAt().toString() : null)
                    .otherUser(otherUser)
                    .lastMessage(conv != null ? conv.getLastMessage() : null)
                    .lastMessageAt(conv != null ? conv.getLastMessageAt() : null)
                    .unreadCount(conv != null ? conv.getUnreadCount() : 0)
                    .build());
        }

        // Sort: most recent conversation first
        result.sort((a, b) -> {
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });

        return result;
    }

    // ── Internal: call user-service ───────────────────────────────────────────

    private OtherUserDto fetchUserProfile(String userId) {
        try {
            OtherUserDto profile = userServiceClient.get()
                    .uri("/api/users/{userId}", userId)
                    // Internal call — pass userId as header (no JWT needed between services)
                    .header("X-User-Id", userId)
                    .retrieve()
                    .bodyToMono(OtherUserDto.class)
                    .block();

            // ✅ Profile valid hai tabhi return karo — naam hona zaroori hai
            if (profile != null
                    && profile.getName() != null
                    && !profile.getName().isBlank()) {
                return profile;
            }
            // Profile mein naam nahi — null return karo, match skip hoga
            log.warn("Profile incomplete for user {}, skipping match", userId);
            return null;
        } catch (Exception e) {
            // user-service down — null return karo
            // Android cache se dikhayega jo pehle save tha
            log.warn("Could not fetch profile for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    // ── Internal: call chat-service ───────────────────────────────────────────

    private ConversationSummaryDto fetchConversation(String conversationId, String requestingUserId) {
        try {
            return chatServiceClient.get()
                    .uri("/api/chats/{conversationId}/summary", conversationId)
                    .header("X-User-Id", requestingUserId)
                    .retrieve()
                    .bodyToMono(ConversationSummaryDto.class)
                    .block();
        } catch (Exception e) {
            log.warn("Could not fetch conversation {}: {}", conversationId, e.getMessage());
            return null; // Silent — match will show without last message
        }
    }


    public long getMatchCount(String userId) {
        return matchRepository.countByUser1IdOrUser2Id(userId, userId);
    }
}