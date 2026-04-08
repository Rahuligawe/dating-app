package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_usages",
       indexes = @Index(columnList = "buyerUserId, code"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReferralUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String ownerUserId;   // Code ka owner
    private String buyerUserId;   // Jisne use kiya

    private int    purchaseAmountPaise;   // Kitne ka subscription liya
    private double pointsAwarded;          // Owner ko kitne points mile
    private double discountApplied;        // Buyer ko kitna discount mila

    // [Gift Points] Agar buyer ne gifted points se purchase kiya
    @Builder.Default
    private boolean usedGiftedPoints = false;
    // Agar true → owner ko NO referral bonus

    @CreationTimestamp
    private LocalDateTime usedAt;
}