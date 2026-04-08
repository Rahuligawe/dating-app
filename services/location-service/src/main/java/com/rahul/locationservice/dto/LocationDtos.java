package com.rahul.locationservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LocationDtos {

    @Data
    public static class UpdateLocationRequest {
        @NotNull
        private Double latitude;
        @NotNull
        private Double longitude;
    }

    @Data
    public static class NearbySettingsRequest {
        @NotNull
        private Boolean enabled;
        @NotNull
        private Integer distanceKm;
    }
}