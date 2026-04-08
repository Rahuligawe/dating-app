package com.rahul.radarservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "radar_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RadarPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RadarMood mood;

    private String customDescription;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Integer distanceRangeKm = 10;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum RadarMood {
        COFFEE, WALK, PARTY, DATE, FOOD, GYM, CUSTOM
    }
}