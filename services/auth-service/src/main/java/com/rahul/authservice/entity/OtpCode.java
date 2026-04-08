package com.rahul.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mobile;

    @Column(nullable = false, length = 6)
    private String code;

    @Builder.Default
    @Column(name = "is_used",  nullable = false)
    private Boolean used = false;

    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}