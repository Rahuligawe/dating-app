package com.rahul.adminservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ad_impressions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdImpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** BANNER | INTERSTITIAL | REWARDED */
    @Column(name = "ad_type")
    private String adType;

    @Column(name = "watch_time_seconds")
    @Builder.Default
    private Integer watchTimeSeconds = 0;

    /** 2-letter country code: IN, US, UK … */
    @Column(length = 10)
    private String region;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
