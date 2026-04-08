package com.rahul.eventservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_comments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 500)
    private String comment;

    @CreationTimestamp
    private LocalDateTime createdAt;
}