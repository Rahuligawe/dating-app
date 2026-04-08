package com.rahul.subscriptionservice.service;

import com.rahul.subscriptionservice.entity.SubscriptionPlan;
import com.rahul.subscriptionservice.entity.UserSubscription;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import com.rahul.subscriptionservice.repository.SubscriptionPlanRepository;
import com.rahul.subscriptionservice.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository     subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;        // [NEW]
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReferralService            referralService;
    private final RazorpayService            razorpayService;

    // Prices from DB — fallback hardcoded
    public static final Map<Plan, Integer> MONTHLY_PRICES = Map.of(
            Plan.FREE,    0,
            Plan.PREMIUM, 9900,
            Plan.ULTRA,   29900
    );
    public static final Map<Plan, Integer> YEARLY_PRICES = Map.of(
            Plan.FREE,    0,
            Plan.PREMIUM, 69900,
            Plan.ULTRA,   199900
    );

    public UserSubscription getSubscription(String userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(sub -> {
                    // If paid plan and endDate has passed → auto-expire to FREE
                    if (sub.getPlan() != Plan.FREE
                            && sub.getEndDate() != null
                            && sub.getEndDate().isBefore(LocalDateTime.now())) {
                        expireSingle(sub);
                    }
                    return sub;
                })
                .orElse(UserSubscription.builder()
                        .userId(userId).plan(Plan.FREE).isActive(true).build());
    }

    // ── Scheduled Expiry — runs every 30 minutes ──────────────────────────────
    @Scheduled(fixedRate = 30 * 60 * 1000)
    @Transactional
    public void expireSubscriptions() {
        List<UserSubscription> expired = subscriptionRepository
                .findByPlanNotAndEndDateBeforeAndIsActiveTrue(Plan.FREE, LocalDateTime.now());
        if (expired.isEmpty()) return;
        log.info("Expiring {} subscription(s)", expired.size());
        expired.forEach(this::expireSingle);
    }

    private void expireSingle(UserSubscription sub) {
        log.info("Subscription expired for user {}: was {}", sub.getUserId(), sub.getPlan());
        sub.setPlan(Plan.FREE);
        sub.setIsActive(false);
        sub.setEndDate(null);
        subscriptionRepository.save(sub);
        // Remove Redis premium flag
        redisTemplate.delete("user:premium:" + sub.getUserId());
        // Notify user-service via Kafka to reset subscriptionType on profile
        try {
            kafkaTemplate.send("subscription.updated", sub.getUserId(),
                    Map.of("userId", sub.getUserId(), "plan", "FREE"));
        } catch (Exception e) {
            log.warn("Kafka notify failed for expiry (non-fatal): {}", e.getMessage());
        }
    }

    // ── [UPDATED] getPlanFeatures — DB se, fallback hardcoded ────────────────
    public PlanFeatures getPlanFeatures(Plan plan) {
        // DB se try karo
        return planRepository.findById(plan)
                .map(this::toPlanFeatures)
                .orElseGet(() -> hardcodedFeatures(plan)); // fallback
    }

    // DB entity → PlanFeatures DTO
    private PlanFeatures toPlanFeatures(SubscriptionPlan p) {
        return PlanFeatures.builder()
                .plan(p.getPlan())
                .dailySwipes(p.getDailySwipes())
                .unlimitedSwipes(p.isUnlimitedSwipes())
                .seeWhoLikedYou(p.isSeeWhoLikedYou())
                .superLikesPerDay(p.getSuperLikesPerDay())
                .hasAds(p.isHasAds())
                .readReceipts(p.isReadReceipts())
                .profileBoostsPerWeek(p.getProfileBoostsPerWeek())
                .aiCompatibilityScore(p.isAiCompatibilityScore())
                .travelVisibilityBoost(p.isTravelVisibilityBoost())
                .topProfileRanking(p.isTopProfileRanking())
                .priceMonthly(p.getPriceMonthly())
                .priceYearly(p.getPriceYearly())
                .build();
    }

    // Hardcoded fallback — DB down ho to bhi app chalti rahe
    private PlanFeatures hardcodedFeatures(Plan plan) {
        return switch (plan) {
            case FREE    -> PlanFeatures.builder().plan(Plan.FREE)
                    .dailySwipes(50).unlimitedSwipes(false).seeWhoLikedYou(false)
                    .superLikesPerDay(0).hasAds(true)
                    .priceMonthly(0).priceYearly(0).build();
            case PREMIUM -> PlanFeatures.builder().plan(Plan.PREMIUM)
                    .unlimitedSwipes(true).seeWhoLikedYou(true)
                    .superLikesPerDay(5).hasAds(false).readReceipts(true)
                    .profileBoostsPerWeek(1).priceMonthly(99).priceYearly(699).build();
            case ULTRA   -> PlanFeatures.builder().plan(Plan.ULTRA)
                    .unlimitedSwipes(true).seeWhoLikedYou(true)
                    .superLikesPerDay(-1).hasAds(false).readReceipts(true)
                    .profileBoostsPerWeek(10).aiCompatibilityScore(true)
                    .topProfileRanking(true).travelVisibilityBoost(true)
                    .priceMonthly(299).priceYearly(1999).build();
        };
    }

    // ── Create Razorpay Order ─────────────────────────────────────────────────
    public Map<String, Object> createPaymentOrder(String userId, Plan plan,
                                                  boolean yearly, String referralCode) {
        // [Fix] Prices bhi DB se lo agar available ho
        PlanFeatures features = getPlanFeatures(plan);
        int baseAmountPaise = yearly
                ? (features.getPriceYearly() * 100)
                : (features.getPriceMonthly() * 100);

        // Fallback to hardcoded if DB returned 0
        if (baseAmountPaise == 0 && plan != Plan.FREE) {
            baseAmountPaise = yearly ? YEARLY_PRICES.get(plan) : MONTHLY_PRICES.get(plan);
        }

        int finalAmountPaise = baseAmountPaise;
        double discountPercent = 0;

        if (referralCode != null && !referralCode.isBlank()) {
            Map<String, Object> validation = referralService.validateCode(referralCode, userId);
            if ((boolean) validation.get("valid")) {
                discountPercent  = (double) validation.get("discountPercent");
                double discount  = baseAmountPaise * (discountPercent / 100.0);
                finalAmountPaise = (int) (baseAmountPaise - discount);
            }
        }

        String receipt = "ord_" + (System.currentTimeMillis() % 1000000000L);
        Map<String, Object> order = razorpayService.createOrder(finalAmountPaise, receipt);

        return Map.of(
                "orderId",         order.get("orderId"),
                "amount",          finalAmountPaise,
                "baseAmount",      baseAmountPaise,
                "discountPercent", discountPercent,
                "currency",        "INR",
                "keyId",           order.get("keyId"),
                "plan",            plan.name(),
                "yearly",          yearly
        );
    }

    // ── Verify + Activate ─────────────────────────────────────────────────────
    @Transactional
    public UserSubscription verifyAndActivate(String userId, Plan plan,
                                              boolean yearly,
                                              String razorpayOrderId,
                                              String razorpayPaymentId,
                                              String razorpaySignature,
                                              String referralCode,
                                              boolean usedGiftedPoints) {
        if (!razorpayService.verifySignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            throw new RuntimeException("Invalid payment signature");
        }

        PlanFeatures features = getPlanFeatures(plan);
        int amountPaise = yearly
                ? (features.getPriceYearly() * 100)
                : (features.getPriceMonthly() * 100);
        if (amountPaise == 0 && plan != Plan.FREE)
            amountPaise = yearly ? YEARLY_PRICES.get(plan) : MONTHLY_PRICES.get(plan);

        if (referralCode != null && !referralCode.isBlank()) {
            referralService.applyReferral(referralCode, userId, amountPaise, usedGiftedPoints);
        }
        return upgradePlan(userId, plan, razorpayPaymentId, "RAZORPAY", yearly);
    }

    @Transactional
    public UserSubscription activateViaPoints(String userId, Plan plan) {
        Map<String, Object> result = referralService.redeemForSubscription(userId, plan);
        if (!(boolean) result.get("success"))
            throw new RuntimeException((String) result.get("message"));
        return upgradePlan(userId, plan, "POINTS_REDEMPTION", "POINTS", false);
    }

    @Transactional
    public UserSubscription upgradePlan(String userId, Plan newPlan,
                                        String paymentId, String paymentProvider,
                                        boolean yearly) {
        UserSubscription sub = subscriptionRepository.findByUserId(userId)
                .orElse(UserSubscription.builder().userId(userId).build());
        sub.setPlan(newPlan);
        sub.setPaymentId(paymentId);
        sub.setPaymentProvider(paymentProvider);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(yearly
                ? LocalDateTime.now().plusYears(1)
                : LocalDateTime.now().plusMonths(1));
        sub.setIsActive(true);
        UserSubscription saved = subscriptionRepository.save(sub);

        if (newPlan != Plan.FREE) {
            int ttlDays = yearly ? 366 : 32;
            redisTemplate.opsForValue().set("user:premium:" + userId, "true", ttlDays, TimeUnit.DAYS);
        }
        kafkaTemplate.send("subscription.updated", userId,
                Map.of("userId", userId, "plan", newPlan.name()));
        kafkaTemplate.send("notification.send", userId, Map.of(
                "userId", userId, "type", "SUBSCRIPTION_UPGRADED",
                "title",  "Welcome to " + newPlan.name() + "! 🎉",
                "body",   "Your plan has been upgraded successfully"));
        referralService.generateCodeForUser(userId);
        log.info("Subscription activated: user={} plan={}", userId, newPlan);
        return saved;
    }

    // Backward compat
    @Transactional
    public UserSubscription upgradePlan(String userId, Plan newPlan,
                                        String paymentId, String paymentProvider) {
        return upgradePlan(userId, newPlan, paymentId, paymentProvider, false);
    }

    @lombok.Data @lombok.Builder
    public static class PlanFeatures {
        private Plan    plan;
        private int     dailySwipes;
        private boolean unlimitedSwipes;
        private boolean seeWhoLikedYou;
        private int     superLikesPerDay;
        private boolean hasAds;
        private boolean readReceipts;
        private int     profileBoostsPerWeek;
        private boolean aiCompatibilityScore;
        private boolean travelVisibilityBoost;
        private boolean topProfileRanking;
        private int     priceMonthly;
        private int     priceYearly;
    }
}