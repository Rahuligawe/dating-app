package com.rahul.adminservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_sessions", indexes = {
    @Index(name = "idx_app_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_app_sessions_start",   columnList = "session_start")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;

    @Column(name = "session_end")
    private LocalDateTime sessionEnd;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
