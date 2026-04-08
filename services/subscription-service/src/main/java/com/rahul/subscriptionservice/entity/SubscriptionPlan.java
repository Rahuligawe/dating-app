package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

// [NEW] DB mein plan features store karo — dynamically update kar sako
@Entity
@Table(name = "subscription_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionPlan {

    @Id
    @Enumerated(EnumType.STRING)
    private UserSubscription.Plan plan;  // FREE, PREMIUM, ULTRA

    // Swipes
    @Builder.Default private int     dailySwipes          = 50;
    @Builder.Default private boolean unlimitedSwipes      = false;

    // Features
    @Builder.Default private boolean seeWhoLikedYou       = false;
    @Builder.Default private int     superLikesPerDay     = 0;   // -1 = unlimited
    @Builder.Default private boolean hasAds               = true;
    @Builder.Default private boolean readReceipts         = false;
    @Builder.Default private int     profileBoostsPerWeek = 0;
    @Builder.Default private boolean aiCompatibilityScore = false;
    @Builder.Default private boolean travelVisibilityBoost= false;
    @Builder.Default private boolean topProfileRanking    = false;
    @Builder.Default private int     maxPhotos            = 5;

    // Pricing (INR)
    @Builder.Default private int priceMonthly = 0;
    @Builder.Default private int priceYearly  = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}