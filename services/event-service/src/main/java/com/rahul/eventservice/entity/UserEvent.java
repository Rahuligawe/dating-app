package com.rahul.eventservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    private String locationName;
    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    private Visibility visibility = Visibility.MATCHES;

    private Integer likeCount    = 0;
    private Integer commentCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum EventType {
        TRAVEL, POOJA, PARTY, MEETUP, BIRTHDAY, CUSTOM
    }

    public enum Visibility {
        MATCHES, FRIENDS, PUBLIC
    }
}