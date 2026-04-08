package com.rahul.moodservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "mood_dislikes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"moodId", "userId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodDislike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String moodId;

    @Column(nullable = false)
    private String userId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
