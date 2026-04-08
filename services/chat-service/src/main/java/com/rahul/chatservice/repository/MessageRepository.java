package com.rahul.chatservice.repository;

import com.rahul.chatservice.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository
        extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderBySentAtDesc(
            String conversationId, Pageable pageable);

    // Messages not sent by userId and not yet seen
    @Query("{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'seen': false }")
    List<Message> findUnseenMessages(String conversationId, String userId);

    long countByConversationIdAndSeenFalseAndSenderIdNot(
            String conversationId, String userId);

    // ✅ CORRECT: MongoDB @Query with count = true
    @Query(value = "{ 'conversationId': ?0, 'senderId': { $ne: ?1 }, 'seen': false }",
            count = true)
    int countUnseenByConversationIdAndNotSender(
            String conversationId,
            String userId
    );
}