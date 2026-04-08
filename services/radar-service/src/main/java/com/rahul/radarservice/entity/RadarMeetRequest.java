package com.rahul.radarservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "radar_meet_requests",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"radarId", "fromUserId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RadarMeetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String radarId;

    @Column(nullable = false)
    private String fromUserId;

    @Column(nullable = false)
    private String toUserId;

    @Enumerated(EnumType.STRING)
    private MeetStatus status = MeetStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum MeetStatus {
        PENDING, ACCEPTED, REJECTED
    }
}