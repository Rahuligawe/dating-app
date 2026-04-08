package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReferralCode {

    @Id
    private String code;           // "RAH123" — userId se generate hoga

    @Column(nullable = false, unique = true)
    private String ownerUserId;    // Jiska code hai

    // [Special] DB se override kar sakte ho — default 10%
    @Builder.Default
    private double discountPercent = 10.0;   // Buyer ko discount

    @Builder.Default
    private double bonusPercent = 10.0;      // Owner ko points (% of purchase)

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private long totalUses = 0;

    @Builder.Default
    private double totalPointsEarned = 0.0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}