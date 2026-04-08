package com.rahul.chatservice.repository;

import com.rahul.chatservice.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConversationRepository
        extends MongoRepository<Conversation, String> {

    List<Conversation> findByUser1IdOrUser2IdOrderByLastMessageAtDesc(
            String user1Id, String user2Id);

    boolean existsByUser1IdAndUser2Id(String user1Id, String user2Id);
}