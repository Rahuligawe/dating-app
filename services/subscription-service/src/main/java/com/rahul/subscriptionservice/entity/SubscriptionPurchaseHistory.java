package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_purchase_history",
       indexes = @Index(columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionPurchaseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserSubscription.Plan plan;

    private String paymentId;
    private String paymentProvider;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Builder.Default
    private boolean isRenewal = false;

    @CreationTimestamp
    private LocalDateTime purchasedAt;
}
