package com.rahul.adminservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_sessions", indexes = {
    @Index(columnList = "adminUserId"),
    @Index(columnList = "tokenHash")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String adminUserId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    private String ipAddress;
    private String userAgent;
    private String deviceInfo;

    @CreationTimestamp
    private LocalDateTime loginAt;

    private LocalDateTime lastActiveAt;

    @Builder.Default
    private boolean isRevoked = false;
}
