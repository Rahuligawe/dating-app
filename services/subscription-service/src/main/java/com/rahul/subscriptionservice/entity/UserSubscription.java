package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    private String paymentId;
    private String paymentProvider;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime cancelledAt;

    // For PREMIUM/ULTRA auto-renewing subscriptions via Cashfree
    private String cashfreeSubscriptionId;

    @Builder.Default
    private Boolean isAutoRenew = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // FREE < WEEKLY < PREMIUM < ULTRA
    public enum Plan { FREE, WEEKLY, PREMIUM, ULTRA }
}