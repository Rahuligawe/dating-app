package com.rahul.subscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "points_wallets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointsWallet {

    @Id
    private String userId;

    @Builder.Default
    private double balance = 0.0;          // Current points

    @Builder.Default
    private double totalEarned = 0.0;      // Lifetime earned

    @Builder.Default
    private double totalRedeemed = 0.0;    // Lifetime redeemed

    @Builder.Default
    private double gifted = 0.0;           // Total gifted to others

    // [Gift] Received points — ye "gifted" points hain
    // Agar in points se subscription liya to referral bonus nahi milega
    @Builder.Default
    private double giftedBalance = 0.0;    // Gifted points ka balance (subset of balance)

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}