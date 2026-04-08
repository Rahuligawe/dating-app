package com.rahul.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {
    private String id;
    private String lastMessage;
    private String lastMessageAt;
    private int unreadCount;
}