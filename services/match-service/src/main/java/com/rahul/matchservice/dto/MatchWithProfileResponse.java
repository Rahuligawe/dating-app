package com.rahul.matchservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for GET /api/matches/with-profiles
 * Combines: Match + OtherUser's profile (from user-service) + last message info (from chat-service)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchWithProfileResponse {

    private String matchId;
    private String conversationId;   // same as matchId (chat-service uses matchId as conversationId)
    private String matchedAt;

    // Other user's profile (fetched from user-service)
    private OtherUserDto otherUser;

    // Last message info (fetched from chat-service)
    private String lastMessage;
    private String lastMessageAt;
    private int unreadCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OtherUserDto {
        private String id;
        private String name;
        private Integer age;
        private String city;
        private String bio;
        private String gender;
        private String profilePhotoUrl;
        private String subscriptionType;
        private Boolean isVerified;
    }
}