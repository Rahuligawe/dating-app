package com.rahul.userservice.dto;

import com.rahul.userservice.entity.UserProfile;
import com.rahul.userservice.entity.UserProfile.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public class UserDtos {

    @Data
    public static class UpdateProfileRequest {
        private String name;
        private LocalDate dateOfBirth;
        private Gender gender;
        private List<RelationshipType> lookingFor;
        private String bio;
        private List<String> interests;
        private GenderPreference genderPreference;
        private Integer maxDistanceKm;
        private Integer minAgePreference;
        private Integer maxAgePreference;
        private String city;
        // Device & app registration fields (Konvo Talk + AuraLink)
        private String mobile;      // 10-digit number
        private String androidId;
        private String deviceName;
        private String deviceBrand;
        private String appSource;   // "AURALINK" | "KONVO_TALK"
    }

    @Data @Builder
    public static class UserProfileResponse {
        private String id;
        private String name;
        private Integer age;
        private Gender gender;
        private GenderPreference genderPreference;
        private String bio;
        private List<String> photos;
        private List<String> interests;
        private List<RelationshipType> lookingFor;
        private String city;
        private Integer minAgePreference;
        private Integer maxAgePreference;
        private Integer maxDistanceKm;
        private Boolean isVerified;
        private SubscriptionType subscriptionType;
        private Boolean isProfileComplete;
        private Double compatibilityScore; // AI-computed, optional
        private String profilePhotoUrl;
    }

    // Response for Konvo Talk's "Find Online User by mobile number" feature
    @Data @Builder
    public static class KonvoUserResponse {
        private String userId;
        private String name;
        private String mobile;
        private String profilePhotoUrl;
        private String androidId;
        private String deviceName;
        private String deviceBrand;
        private boolean registeredOnKonvo; // false → show "Send Invite" in Android
    }

    @Data
    public static class NearbyAlertSettings {
        private Boolean enabled;
        private Integer distanceKm;
    }
}