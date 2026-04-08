package com.rahul.moodservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mood_status")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MoodType moodType;

    @Column(length = 300)
    private String description;

    private Double latitude;
    private Double longitude;
    private String locationName;

    private Integer distanceRangeKm = 15;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount    = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer dislikeCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer commentCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum MoodType {
        COFFEE, WALK, GYM, PARTY, DATE,
        MOVIE, TRAVEL_BUDDY, BORED, FOOD,
        WORK, CUSTOM
    }
}