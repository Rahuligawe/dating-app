package com.rahul.eventservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_likes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"eventId", "userId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String userId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}