package com.rahul.swipeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "swipes",
    indexes = {
        @Index(name = "idx_swipe_from", columnList = "fromUserId"),
        @Index(name = "idx_swipe_to",   columnList = "toUserId")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_swipe_from_to",
                          columnNames = {"fromUserId", "toUserId"})
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Swipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fromUserId;

    @Column(nullable = false)
    private String toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwipeAction action;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum SwipeAction { LIKE, DISLIKE, SUPER_LIKE }
}