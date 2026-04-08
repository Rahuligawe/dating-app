package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

// Points ka history — wallet screen mein transactions dikhane ke liye
@Entity
@Table(name = "points_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private double amount;           // Positive = credit, Negative = debit

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private String description;      // "Referral bonus from RAHUL91"
    private String referenceId;      // Razorpay order ID ya referral code

    @Builder.Default
    private boolean isGifted = false; // Gifted points transaction

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum TransactionType {
        REFERRAL_BONUS,    // Referral se mila
        GIFT_RECEIVED,     // Kisi ne gift kiya
        GIFT_SENT,         // Tumne gift kiya
        REDEEMED_CASH,     // Encash kiya
        REDEEMED_SUB,      // Subscription purchase mein use kiya
        BONUS              // Admin ne diya
    }
}