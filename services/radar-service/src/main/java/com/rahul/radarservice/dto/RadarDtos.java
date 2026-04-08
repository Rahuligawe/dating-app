package com.rahul.radarservice.dto;

import com.rahul.radarservice.entity.RadarPost.RadarMood;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class RadarDtos {

    @Data
    public static class GoLiveRequest {

        @NotNull(message = "Mood is required")
        private RadarMood mood;

        private String customDescription;

        @NotNull(message = "Latitude is required")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        private Double longitude;

        private Integer distanceRangeKm = 10;

        // Duration in minutes: 30, 60, or 120
        private Integer durationMinutes = 60;
    }

    @Data
    public static class RespondMeetRequest {
        @NotNull
        private Long meetRequestId;
        @NotNull
        private Boolean accept;
    }
}