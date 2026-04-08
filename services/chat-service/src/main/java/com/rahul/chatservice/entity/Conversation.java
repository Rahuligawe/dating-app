package com.rahul.chatservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id
    private String id; // this is the matchId from match-service

    @Indexed
    private String user1Id;

    @Indexed
    private String user2Id;

    private String lastMessage;

    private String lastMessageType;

    @Indexed
    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}