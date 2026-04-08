package com.rahul.chatservice.dto;

import com.rahul.chatservice.entity.Message.MessageType;
import lombok.Data;

@Data
public class SendMessageRequest {
    private String senderId;
    private String receiverId;
    private String text;
    private MessageType type;
    private String mediaUrl;
    private Double locationLat;
    private Double locationLong;
}