package com.rahul.matchservice.service;

import com.rahul.matchservice.dto.ConversationSummaryDto;
import com.rahul.matchservice.dto.MatchWithProfileResponse;
import com.rahul.matchservice.dto.MatchWithProfileResponse.OtherUserDto;
import com.rahul.matchservice.entity.Match;
import com.rahul.matchservice.repository.MatchRepository;
import com.rahul.matchservice.stream.StreamPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final StreamPublisher streamPublisher;
    private final WebClient userServiceClient;
    private final WebClient chatServiceClient;

    public MatchService(
            MatchRepository matchRepository,
            StreamPublisher streamPublisher,
            @Qualifier("userServiceClient") WebClient userServiceClient,
            @Qualifier("chatServiceClient") WebClient chatServiceClient) {
        this.matchRepository   = matchRepository;
        this.streamPublisher   = streamPublisher;
        this.userServiceClient = userServiceClient;
        this.chatServiceClient = chatServiceClient;
    }

    // ─── Called by StreamConsumerConfig when match.create arrives ────────────

    @Transactional
    public void processMatchCreate(Map<String, String> event) {
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
        streamPublisher.publish("notification.send", Map.of(
                "userId", user1Id, "type", "NEW_MATCH",
                "title", "It's a Match! 💘", "body", "You have a new match!",
                "data", Map.of("matchId", match.getId(), "matchedUserId", user2Id)
        ));
        streamPublisher.publish("notification.send", Map.of(
                "userId", user2Id, "type", "NEW_MATCH",
                "title", "It's a Match! 💘", "body", "You have a new match!",
                "data", Map.of("matchId", match.getId(), "matchedUserId", user1Id)
        ));

        // Tell chat-service to create a conversation
        streamPublisher.publish("chat.create.conversation", Map.of(
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

    public List<MatchWithProfileResponse> getMatchesWithProfiles(String userId) {
        List<Match> matches = matchRepository.findByUser1IdOrUser2Id(userId, userId);
        List<MatchWithProfileResponse> result = new ArrayList<>();

        for (Match match : matches) {
            if (Boolean.FALSE.equals(match.getIsActive())) continue;

            String otherUserId = match.getUser1Id().equals(userId)
                    ? match.getUser2Id()
                    : match.getUser1Id();

            OtherUserDto otherUser = fetchUserProfile(otherUserId);
            if (otherUser == null) continue;

            ConversationSummaryDto conv = fetchConversation(match.getId(), userId);

            result.add(MatchWithProfileResponse.builder()
                    .matchId(match.getId())
                    .conversationId(match.getId())
                    .matchedAt(match.getMatchedAt() != null ? match.getMatchedAt().toString() : null)
                    .otherUser(otherUser)
                    .lastMessage(conv != null ? conv.getLastMessage() : null)
                    .lastMessageAt(conv != null ? conv.getLastMessageAt() : null)
                    .unreadCount(conv != null ? conv.getUnreadCount() : 0)
                    .build());
        }

        result.sort((a, b) -> {
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });

        return result;
    }

    private OtherUserDto fetchUserProfile(String userId) {
        try {
            OtherUserDto profile = userServiceClient.get()
                    .uri("/api/users/{userId}", userId)
                    .header("X-User-Id", userId)
                    .retrieve()
                    .bodyToMono(OtherUserDto.class)
                    .block();

            if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
                return profile;
            }
            log.warn("Profile incomplete for user {}, skipping match", userId);
            return null;
        } catch (Exception e) {
            log.warn("Could not fetch profile for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

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
            return null;
        }
    }

    public long getMatchCount(String userId) {
        return matchRepository.countByUser1IdOrUser2Id(userId, userId);
    }

    // Internal — returns all user IDs that are matched with userId
    public List<String> getMatchUserIds(String userId) {
        return matchRepository.findByUser1IdOrUser2Id(userId, userId)
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getIsActive()))
                .map(m -> m.getUser1Id().equals(userId) ? m.getUser2Id() : m.getUser1Id())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}
