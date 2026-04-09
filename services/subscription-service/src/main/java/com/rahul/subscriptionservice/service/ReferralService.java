package com.rahul.subscriptionservice.service;

import com.rahul.subscriptionservice.entity.*;
import com.rahul.subscriptionservice.entity.PointsTransaction.TransactionType;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import com.rahul.subscriptionservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.rahul.subscriptionservice.stream.StreamPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private final ReferralCodeRepository      referralCodeRepo;
    private final ReferralUsageRepository     referralUsageRepo;
    private final PointsWalletRepository      walletRepo;
    private final PointsTransactionRepository txRepo;
    private final StreamPublisher streamPublisher;
    private final RestTemplate                restTemplate; // [NEW] smart code ke liye

    // ── Generate Referral Code for new user ──────────────────────────────────
    @Transactional
    public ReferralCode generateCodeForUser(String userId) {
        return referralCodeRepo.findByOwnerUserId(userId).orElseGet(() -> {
            // [UPDATED] Smart human-readable code generate karo
            String code = generateSmartCode(userId);
            ReferralCode rc = ReferralCode.builder()
                    .code(code)
                    .ownerUserId(userId)
                    .build();
            createWalletIfAbsent(userId);
            return referralCodeRepo.save(rc);
        });
    }

    // ── [NEW] Smart code generation ──────────────────────────────────────────
    // Format: firstname(3) + lastname + birthYear(2 digits)
    // Example: "Rahul Igawe" + 1996 → RAHIGAWE96
    // Collision: RAHIGAWE96 → RAHIGAWE962 → RAHIGAWE963 ...
    private String generateSmartCode(String userId) {
        String name = null;
        Integer birthYear = null;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = restTemplate.getForObject(
                    "http://user-service/api/users/" + userId, Map.class);

            if (profile != null) {
                name = (String) profile.get("name");
                String dob = (String) profile.get("dateOfBirth");
                if (dob != null && dob.length() >= 4) {
                    birthYear = Integer.parseInt(dob.substring(0, 4));
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch profile for smart code: {}", e.getMessage());
        }

        String baseCode = buildBaseCode(name, birthYear, userId);
        return resolveCollision(baseCode);
    }

    private String buildBaseCode(String name, Integer birthYear, String userId) {
        if (name == null || name.isBlank()) {
            // Fallback — userId ke first 6 alphanumeric chars
            return userId.replace("-", "").substring(0, 6).toUpperCase();
        }

        String[] parts = name.trim().toUpperCase().split("\\s+");
        String base;

        if (parts.length >= 2) {
            // "Rahul Igawe" → "RAH" + "IGAWE"
            String first = parts[0].length() >= 3 ? parts[0].substring(0, 3) : parts[0];
            String last  = parts[parts.length - 1];
            base = first + last;
        } else {
            base = parts[0]; // single name — poora naam
        }

        // Birth year — last 2 digits
        String year = "";
        if (birthYear != null) {
            year = String.valueOf(birthYear % 100);
            if (year.length() == 1) year = "0" + year; // 2005 → 05
        }

        String code = base + year;
        return code.length() > 12 ? code.substring(0, 12) : code;
    }

    private String resolveCollision(String baseCode) {
        if (!referralCodeRepo.existsById(baseCode)) return baseCode;

        int suffix = 2;
        while (suffix <= 99) {
            String candidate = baseCode + suffix;
            if (candidate.length() > 14) {
                candidate = baseCode.substring(
                        0, baseCode.length() - String.valueOf(suffix).length()) + suffix;
            }
            if (!referralCodeRepo.existsById(candidate)) return candidate;
            suffix++;
        }
        return baseCode + (int)(Math.random() * 900 + 100);
    }

    // ── Validate Code before purchase ────────────────────────────────────────
    public Map<String, Object> validateCode(String code, String buyerUserId) {
        Optional<ReferralCode> rcOpt = referralCodeRepo.findByCode(code.toUpperCase());

        if (rcOpt.isEmpty() || !rcOpt.get().isActive()) {
            return Map.of("valid", false, "message", "Invalid referral code");
        }

        ReferralCode rc = rcOpt.get();

        if (rc.getOwnerUserId().equals(buyerUserId)) {
            return Map.of("valid", false, "message", "Cannot use your own referral code");
        }

        return Map.of(
                "valid",           true,
                "discountPercent", rc.getDiscountPercent(),
                "message",         "Code applied! You get " + rc.getDiscountPercent() + "% off"
        );
    }

    // ── Apply Referral after successful payment ───────────────────────────────
    @Transactional
    public void applyReferral(String code, String buyerUserId,
                              int purchaseAmountPaise, boolean usedGiftedPoints) {
        if (code == null || code.isBlank()) return;

        ReferralCode rc = referralCodeRepo.findByCode(code.toUpperCase()).orElse(null);
        if (rc == null || !rc.isActive()) return;
        if (rc.getOwnerUserId().equals(buyerUserId)) return;

        double purchaseAmountInr = purchaseAmountPaise / 100.0;

        // [Rule] Gifted points se purchase → NO referral bonus
        double pointsToAward = usedGiftedPoints ? 0.0
                : purchaseAmountInr * (rc.getBonusPercent() / 100.0);

        referralUsageRepo.save(ReferralUsage.builder()
                .code(code.toUpperCase())
                .ownerUserId(rc.getOwnerUserId())
                .buyerUserId(buyerUserId)
                .purchaseAmountPaise(purchaseAmountPaise)
                .pointsAwarded(pointsToAward)
                .discountApplied(rc.getDiscountPercent())
                .usedGiftedPoints(usedGiftedPoints)
                .build());

        rc.setTotalUses(rc.getTotalUses() + 1);
        rc.setTotalPointsEarned(rc.getTotalPointsEarned() + pointsToAward);
        referralCodeRepo.save(rc);

        if (pointsToAward > 0) {
            creditPoints(rc.getOwnerUserId(), pointsToAward,
                    TransactionType.REFERRAL_BONUS,
                    "Referral bonus — " + buyerUserId + " purchased using your code",
                    code, false);

            streamPublisher.publish("notification.send", Map.of(
                    "userId", rc.getOwnerUserId(),
                    "type",   "REFERRAL_BONUS",
                    "title",  "You earned ₹" + String.format("%.1f", pointsToAward) + " points! 🎉",
                    "body",   "Someone used your referral code " + code
            ));
        }

        log.info("Referral applied: code={} buyer={} owner={} points={}",
                code, buyerUserId, rc.getOwnerUserId(), pointsToAward);
    }

    // ── Gift Points ───────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> giftPoints(String fromUserId, String toUserId,
                                          double amount) {
        PointsWallet fromWallet = getOrCreateWallet(fromUserId);

        if (fromWallet.getBalance() < amount) {
            return Map.of("success", false, "message", "Insufficient points balance");
        }
        if (amount < 1) {
            return Map.of("success", false, "message", "Minimum gift is 1 point");
        }

        fromWallet.setBalance(fromWallet.getBalance() - amount);
        fromWallet.setGifted(fromWallet.getGifted() + amount);
        walletRepo.save(fromWallet);
        debitPoints(fromUserId, amount, TransactionType.GIFT_SENT,
                "Gifted to " + toUserId, toUserId, false);

        PointsWallet toWallet = getOrCreateWallet(toUserId);
        toWallet.setBalance(toWallet.getBalance() + amount);
        toWallet.setTotalEarned(toWallet.getTotalEarned() + amount);
        toWallet.setGiftedBalance(toWallet.getGiftedBalance() + amount);
        walletRepo.save(toWallet);
        creditPoints(toUserId, amount, TransactionType.GIFT_RECEIVED,
                "Gift from " + fromUserId, fromUserId, true);

        streamPublisher.publish("notification.send", Map.of(
                "userId", toUserId,
                "type",   "GIFT_RECEIVED",
                "title",  "You received ₹" + String.format("%.0f", amount) + " points! 🎁",
                "body",   "A friend sent you points as a gift"
        ));

        return Map.of("success", true,
                "message", "Points gifted successfully",
                "newBalance", fromWallet.getBalance());
    }

    // ── Redeem Points for Subscription ───────────────────────────────────────
    @Transactional
    public Map<String, Object> redeemForSubscription(String userId, Plan plan) {
        PointsWallet wallet = getOrCreateWallet(userId);

        int priceInr = switch (plan) {
            case PREMIUM -> 99;
            case ULTRA   -> 299;
            default      -> throw new RuntimeException("Cannot purchase FREE plan");
        };

        if (wallet.getBalance() < priceInr) {
            return Map.of("success", false,
                    "message", "Need " + priceInr + " points, you have " +
                            (int)wallet.getBalance());
        }

        boolean usingGiftedPoints = wallet.getGiftedBalance() >= priceInr;

        wallet.setBalance(wallet.getBalance() - priceInr);
        wallet.setTotalRedeemed(wallet.getTotalRedeemed() + priceInr);
        if (usingGiftedPoints) {
            wallet.setGiftedBalance(Math.max(0, wallet.getGiftedBalance() - priceInr));
        }
        walletRepo.save(wallet);

        debitPoints(userId, priceInr, TransactionType.REDEEMED_SUB,
                plan.name() + " subscription via points", null, usingGiftedPoints);

        return Map.of(
                "success",           true,
                "plan",              plan.name(),
                "pointsUsed",        priceInr,
                "remainingBalance",  wallet.getBalance(),
                "usedGiftedPoints",  usingGiftedPoints
        );
    }

    // ── Redeem Points for Cash ────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> redeemForCash(String userId, double amount,
                                             String upiId) {
        if (amount < 100) {
            return Map.of("success", false, "message", "Minimum encash amount is 100 points");
        }

        PointsWallet wallet = getOrCreateWallet(userId);
        if (wallet.getBalance() < amount) {
            return Map.of("success", false, "message", "Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setTotalRedeemed(wallet.getTotalRedeemed() + amount);
        walletRepo.save(wallet);

        debitPoints(userId, amount, TransactionType.REDEEMED_CASH,
                "Encashed to UPI: " + upiId, upiId, false);

        log.info("Payout request: userId={} amount={} upiId={}", userId, amount, upiId);

        return Map.of("success", true,
                "message", "₹" + (int)amount + " will be transferred to " + upiId + " within 24 hours",
                "newBalance", wallet.getBalance());
    }

    // ── Get Wallet & Transactions ─────────────────────────────────────────────
    public PointsWallet getWallet(String userId) {
        return getOrCreateWallet(userId);
    }

    public List<PointsTransaction> getTransactions(String userId) {
        return txRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public ReferralCode getMyCode(String userId) {
        return referralCodeRepo.findByOwnerUserId(userId)
                .orElseGet(() -> generateCodeForUser(userId));
    }

    public java.util.Optional<String> getUserIdByCode(String code) {
        return referralCodeRepo.findByCode(code).map(ReferralCode::getOwnerUserId);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────
    private PointsWallet getOrCreateWallet(String userId) {
        return walletRepo.findByUserId(userId).orElseGet(() -> {
            PointsWallet w = PointsWallet.builder().userId(userId).build();
            return walletRepo.save(w);
        });
    }

    private void createWalletIfAbsent(String userId) {
        walletRepo.findByUserId(userId).orElseGet(() ->
                walletRepo.save(PointsWallet.builder().userId(userId).build())
        );
    }

    private void creditPoints(String userId, double amount, TransactionType type,
                              String desc, String refId, boolean isGifted) {
        txRepo.save(PointsTransaction.builder()
                .userId(userId).amount(amount).type(type)
                .description(desc).referenceId(refId).isGifted(isGifted)
                .build());
        PointsWallet w = getOrCreateWallet(userId);
        w.setBalance(w.getBalance() + amount);
        w.setTotalEarned(w.getTotalEarned() + amount);
        walletRepo.save(w);
    }

    private void debitPoints(String userId, double amount, TransactionType type,
                             String desc, String refId, boolean isGifted) {
        txRepo.save(PointsTransaction.builder()
                .userId(userId).amount(-amount).type(type)
                .description(desc).referenceId(refId).isGifted(isGifted)
                .build());
    }
}