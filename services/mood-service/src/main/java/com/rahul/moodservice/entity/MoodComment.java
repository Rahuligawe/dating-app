package com.rahul.moodservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "mood_comments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String moodId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 300)
    private String comment;

    @CreationTimestamp
    private LocalDateTime createdAt;
}