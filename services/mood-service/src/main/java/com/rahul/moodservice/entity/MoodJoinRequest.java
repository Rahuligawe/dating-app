package com.rahul.moodservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "mood_join_requests",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"moodId", "fromUserId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String moodId;

    @Column(nullable = false)
    private String fromUserId;

    @Enumerated(EnumType.STRING)
    private JoinStatus status = JoinStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum JoinStatus {
        PENDING, ACCEPTED, REJECTED
    }
}