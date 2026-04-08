package com.rahul.matchservice.service;

import com.rahul.matchservice.entity.Match;
import com.rahul.matchservice.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchServiceOld {

    private final MatchRepository matchRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─── Kafka: Create Match ──────────────────────────────────

    @KafkaListener(topics = "match.create", groupId = "match-service")
    @Transactional
    public void handleMatchCreate(Map<String, String> event) {
        String user1Id = event.get("user1Id");
        String user2Id = event.get("user2Id");

        // Prevent duplicate matches
        if (matchRepository.existsByUser1IdAndUser2Id(user1Id, user2Id)
                || matchRepository.existsByUser1IdAndUser2Id(user2Id, user1Id)) {
            log.info("Match already exists between {} and {}", user1Id, user2Id);
            return;
        }

        Match match = matchRepository.save(Match.builder()
                .user1Id(user1Id)
                .user2Id(user2Id)
                .build());

        log.info("✅ Match created: {} <-> {} | matchId: {}",
                user1Id, user2Id, match.getId());

        // Notify user1
        kafkaTemplate.send("notification.send", user1Id, Map.of(
                "userId", user1Id,
                "type",   "NEW_MATCH",
                "title",  "It's a Match! 💘",
                "body",   "You have a new match!",
                "data",   Map.of(
                        "matchId",       match.getId(),
                        "matchedUserId", user2Id)
        ));

        // Notify user2
        kafkaTemplate.send("notification.send", user2Id, Map.of(
                "userId", user2Id,
                "type",   "NEW_MATCH",
                "title",  "It's a Match! 💘",
                "body",   "You have a new match!",
                "data",   Map.of(
                        "matchId",       match.getId(),
                        "matchedUserId", user1Id)
        ));

        // Tell chat-service to create a conversation
        kafkaTemplate.send("chat.create.conversation", match.getId(), Map.of(
                "matchId", match.getId(),
                "user1Id", user1Id,
                "user2Id", user2Id
        ));
    }

    // ─── REST API ─────────────────────────────────────────────

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
}