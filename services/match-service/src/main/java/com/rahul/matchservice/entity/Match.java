package com.rahul.matchservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user1Id", "user2Id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String user1Id;

    @Column(nullable = false)
    private String user2Id;

    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime matchedAt;
}