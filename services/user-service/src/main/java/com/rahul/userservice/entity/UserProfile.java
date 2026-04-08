package com.rahul.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    private String id; // same as auth userId

    private String name;

    private LocalDate dateOfBirth;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_looking_for", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "relationship_type")
    @Enumerated(EnumType.STRING)
    private List<RelationshipType> lookingFor;

    @Column(length = 500)
    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_photos", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "photo_url")
    private List<String> photos;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interest")
    private List<String> interests;

    // Location
    private Double locationLat;
    private Double locationLong;
    private String city;

    // Preferences
    @Enumerated(EnumType.STRING)
    private GenderPreference genderPreference;

    @Builder.Default
    private Integer maxDistanceKm = 50;

    @Builder.Default
    private Integer minAgePreference = 18;

    @Builder.Default
    private Integer maxAgePreference = 45;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private SubscriptionType subscriptionType = SubscriptionType.FREE;

    // Safety
    @Builder.Default
    private Boolean isVerified = false;
    private String verificationPhotoUrl;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isProfileComplete = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Gender { MALE, FEMALE, OTHER }
    public enum GenderPreference { MALE, FEMALE, BOTH }
    public enum RelationshipType {
        FRIENDSHIP, DATING, LONG_TERM, MARRIAGE, CASUAL, PARTY_PARTNER, TRAVEL_PARTNER
    }
    public enum SubscriptionType { FREE, PREMIUM, ULTRA }
}
