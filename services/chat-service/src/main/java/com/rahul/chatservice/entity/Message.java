package com.rahul.chatservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private String senderId;

    private String receiverId;

    private MessageType type;

    private String text;

    private String mediaUrl;

    private Double locationLat;
    private Double locationLong;

    // [Issue 3 - Tick Logic] delivered = receiver ki device ne fetch kiya
    // false = sirf server pe hai (single tick)
    // true  = receiver ne getMessages call kiya (double tick)
    @Builder.Default
    private Boolean delivered = false;

    @Builder.Default
    private Boolean seen = false;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime deliveredAt;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime seenAt;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime sentAt;

    public enum MessageType {
        TEXT, IMAGE, VOICE, VIDEO, LOCATION, GIF
    }
}