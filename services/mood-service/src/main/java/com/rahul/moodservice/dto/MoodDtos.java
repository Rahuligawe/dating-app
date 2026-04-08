package com.rahul.moodservice.dto;

import com.rahul.moodservice.entity.MoodStatus.MoodType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class MoodDtos {

    @Data
    public static class PostMoodRequest {

        @NotNull(message = "Mood type is required")
        private MoodType moodType;

        private String description;

        @NotNull(message = "Latitude is required")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        private Double longitude;

        private String locationName;

        private Integer distanceRangeKm = 15;

        // Duration in hours: 1, 2, or 4
        private Integer durationHours = 2;
    }

    @Data
    public static class AddCommentRequest {
        @NotNull
        private String comment;
    }

    @Data
    public static class RespondJoinRequest {
        @NotNull
        private String joinRequestId;
        @NotNull
        private Boolean accept;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MoodCommentResponse {
        private Long id;
        private String moodId;
        private String userId;
        private String userName;
        private String userPhotoUrl;
        private String comment;
        private String createdAt;
    }
}