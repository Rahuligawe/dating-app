package com.rahul.eventservice.dto;

import com.rahul.eventservice.entity.UserEvent.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

public class EventDtos {

    @Data
    public static class CreateEventRequest {

        @NotNull(message = "Event type is required")
        private EventType eventType;

        @NotBlank(message = "Title is required")
        private String title;

        private String description;
        private String locationName;
        private Double latitude;
        private Double longitude;

        @NotNull(message = "Event date is required")
        private LocalDate eventDate;

        private Visibility visibility = Visibility.MATCHES;
    }

    @Data
    public static class AddCommentRequest {
        @NotBlank(message = "Comment cannot be empty")
        private String comment;
    }
}