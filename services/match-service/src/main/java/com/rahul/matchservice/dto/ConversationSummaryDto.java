package com.rahul.matchservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Used internally to parse chat-service's conversation response.
 * Only fields we need — @JsonIgnoreProperties ignores the rest.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationSummaryDto {
    private String id;
    private String lastMessage;
    private String lastMessageAt;
    private int unreadCount;
}