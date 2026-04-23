package com.rahul.subscriptionservice.service;

import com.rahul.subscriptionservice.entity.SubscriptionPlan;
import com.rahul.subscriptionservice.entity.UserSubscription;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import com.rahul.subscriptionservice.repository.SubscriptionPlanRepository;
import com.rahul.subscriptionservice.repository.SubscriptionRepository;
import com.rahul.subscriptionservice.stream.StreamPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository     subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final StreamPublisher            streamPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReferralService            referralService;
    private final CashfreeService            cashfreeService;

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
        // Notify user-service to reset subscriptionType on profile
        streamPublisher.publish("subscription.updated",
                Map.of("userId", sub.getUserId(), "plan", "FREE"));
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

    // ── Create Cashfree Order ─────────────────────────────────────────────────
    public Map<String, Object> createPaymentOrder(String userId, Plan plan,
                                                  boolean yearly, String referralCode) {
        return createPaymentOrder(userId, plan, yearly, referralCode, null, 0);
    }

    public Map<String, Object> createPaymentOrder(String userId, Plan plan,
                                                  boolean yearly, String referralCode,
                                                  String customerPhone) {
        return createPaymentOrder(userId, plan, yearly, referralCode, customerPhone, 0);
    }

    public Map<String, Object> createPaymentOrder(String userId, Plan plan,
                                                  boolean yearly, String referralCode,
                                                  String customerPhone, int pointsToUse) {
        // Block downgrade / same-plan re-purchase on backend too
        checkUpgradeAllowed(userId, plan);

        PlanFeatures features = getPlanFeatures(plan);
        int baseAmountPaise = yearly
                ? (features.getPriceYearly() * 100)
                : (features.getPriceMonthly() * 100);

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

        // Points discount — validate against actual wallet balance
        int validatedPoints = 0;
        if (pointsToUse > 0) {
            com.rahul.subscriptionservice.entity.PointsWallet wallet =
                    referralService.getWallet(userId);
            validatedPoints = Math.min(pointsToUse, (int) wallet.getBalance());
            validatedPoints = Math.min(validatedPoints, finalAmountPaise / 100); // can't exceed plan price
            finalAmountPaise -= validatedPoints * 100;
            if (finalAmountPaise < 0) finalAmountPaise = 0;
        }

        // Cashfree-compatible order ID (alphanumeric + underscore, max 50 chars)
        String orderId = "ORD_" + userId.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(8, userId.length()))
                + "_" + (System.currentTimeMillis() % 1000000000L);

        Map<String, Object> order = cashfreeService.createOrder(finalAmountPaise, orderId, userId, customerPhone);

        // Store order metadata in Redis for webhook activation (TTL = 2 hours)
        Map<String, Object> meta = new HashMap<>();
        meta.put("userId",       userId);
        meta.put("plan",         plan.name());
        meta.put("yearly",       String.valueOf(yearly));
        meta.put("referralCode", referralCode != null ? referralCode : "");
        meta.put("pointsToUse",  String.valueOf(validatedPoints));
        redisTemplate.opsForHash().putAll("order:meta:" + orderId, meta);
        redisTemplate.expire("order:meta:" + orderId, 2, TimeUnit.HOURS);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId",          order.get("orderId"));
        result.put("paymentSessionId", order.get("paymentSessionId"));
        result.put("appId",            order.get("appId"));
        result.put("amount",           finalAmountPaise);
        result.put("baseAmount",       baseAmountPaise);
        result.put("discountPercent",  discountPercent);
        result.put("currency",         "INR");
        result.put("plan",             plan.name());
        result.put("yearly",           yearly);
        result.put("pointsUsed",       validatedPoints);
        return result;
    }

    // ── Verify + Activate ─────────────────────────────────────────────────────
    @Transactional
    public UserSubscription verifyAndActivate(String userId, Plan plan,
                                              boolean yearly,
                                              String cashfreeOrderId,
                                              String referralCode,
                                              boolean usedGiftedPoints) {
        return verifyAndActivate(userId, plan, yearly, cashfreeOrderId, referralCode, usedGiftedPoints, 0);
    }

    @Transactional
    public UserSubscription verifyAndActivate(String userId, Plan plan,
                                              boolean yearly,
                                              String cashfreeOrderId,
                                              String referralCode,
                                              boolean usedGiftedPoints,
                                              int pointsToUse) {
        if (!cashfreeService.verifyPayment(cashfreeOrderId)) {
            throw new RuntimeException("Payment not completed or invalid order");
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
        // Deduct points used during checkout
        if (pointsToUse > 0) {
            referralService.deductPointsForPurchase(userId, pointsToUse);
        }
        return upgradePlan(userId, plan, cashfreeOrderId, "CASHFREE", yearly);
    }

    @Transactional
    public UserSubscription activateViaPoints(String userId, Plan plan, boolean yearly, int pointsToUse) {
        // Same upgrade/downgrade guard as payment flow
        checkUpgradeAllowed(userId, plan);

        if (pointsToUse > 0) {
            // Explicit deduction — caller validated the amount
            referralService.deductPointsForPurchase(userId, pointsToUse);
        } else {
            // Legacy: deduct full plan price in points
            Map<String, Object> result = referralService.redeemForSubscription(userId, plan, yearly);
            if (!(boolean) result.get("success"))
                throw new RuntimeException((String) result.get("message"));
        }
        return upgradePlan(userId, plan, "POINTS_REDEMPTION", "POINTS", yearly);
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
        sub.setCancelledAt(null);
        UserSubscription saved = subscriptionRepository.save(sub);

        if (newPlan != Plan.FREE) {
            int ttlDays = yearly ? 366 : 32;
            redisTemplate.opsForValue().set("user:premium:" + userId, "true", ttlDays, TimeUnit.DAYS);
        }
        streamPublisher.publish("subscription.updated",
                Map.of("userId", userId, "plan", newPlan.name()));
        streamPublisher.publish("notification.send", Map.of(
                "userId", userId, "type", "SUBSCRIPTION_UPGRADED",
                "title",  "Welcome to " + newPlan.name() + "! 🎉",
                "body",   "Your plan has been upgraded successfully"));
        referralService.generateCodeForUser(userId);
        log.info("Subscription activated: user={} plan={}", userId, newPlan);
        return saved;
    }

    // ── Webhook Activation (called by Cashfree webhook) ───────────────────────
    @Transactional
    public void activateByWebhook(String orderId) {
        // 1. Verify payment with Cashfree API
        if (!cashfreeService.verifyPayment(orderId)) {
            log.warn("Webhook: payment not confirmed for orderId={}", orderId);
            return;
        }
        // 2. Look up order metadata from Redis
        Map<Object, Object> meta = redisTemplate.opsForHash().entries("order:meta:" + orderId);
        if (meta == null || meta.isEmpty()) {
            log.warn("Webhook: no metadata in Redis for orderId={} — may already be activated", orderId);
            return;
        }
        String userId       = (String) meta.get("userId");
        Plan   plan         = Plan.valueOf((String) meta.get("plan"));
        boolean yearly      = "true".equals(meta.get("yearly"));
        String referralCode = (String) meta.get("referralCode");

        // 3. Check if already activated (idempotency)
        UserSubscription existing = subscriptionRepository.findByUserId(userId).orElse(null);
        if (existing != null && orderId.equals(existing.getPaymentId())) {
            log.info("Webhook: orderId={} already activated for userId={}", orderId, userId);
            return;
        }
        // 4. Activate
        if (referralCode != null && !referralCode.isBlank()) {
            referralService.applyReferral(referralCode, userId, 0, false);
        }
        // Deduct points used at checkout (stored in Redis meta)
        String pointsToUseStr = (String) meta.get("pointsToUse");
        int pointsToUse = (pointsToUseStr != null && !pointsToUseStr.isBlank())
                ? Integer.parseInt(pointsToUseStr) : 0;
        if (pointsToUse > 0) {
            referralService.deductPointsForPurchase(userId, pointsToUse);
        }
        upgradePlan(userId, plan, orderId, "CASHFREE", yearly);
        redisTemplate.delete("order:meta:" + orderId);
        log.info("Webhook: activated userId={} plan={} yearly={} pointsUsed={}",
                userId, plan, yearly, pointsToUse);
    }

    // ── Cancel Subscription ───────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> cancelSubscription(String userId) {
        UserSubscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No subscription found"));

        if (sub.getPlan() == Plan.FREE) {
            throw new RuntimeException("Free plan cannot be cancelled");
        }
        if (sub.getCancelledAt() != null) {
            throw new RuntimeException("Subscription is already cancelled");
        }

        sub.setCancelledAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        log.info("Subscription cancelled: userId={} plan={} endDate={}", userId, sub.getPlan(), sub.getEndDate());

        return Map.of(
                "success",     true,
                "plan",        sub.getPlan().name(),
                "endDate",     sub.getEndDate() != null ? sub.getEndDate().toString() : "",
                "cancelledAt", sub.getCancelledAt().toString()
        );
    }

    // ── Plan Hierarchy Guard ──────────────────────────────────────────────────
    // FREE < PREMIUM < ULTRA (ordinal order matches enum declaration)
    // Blocks: same-plan re-purchase and downgrades while subscription is still active
    private void checkUpgradeAllowed(String userId, Plan newPlan) {
        UserSubscription current = getSubscription(userId);
        Plan currentPlan = current.getPlan();
        boolean isActive = Boolean.TRUE.equals(current.getIsActive())
                        && current.getEndDate() != null
                        && current.getEndDate().isAfter(LocalDateTime.now());

        if (currentPlan == Plan.FREE || !isActive) return; // always allow upgrade from FREE / expired

        if (newPlan.ordinal() == currentPlan.ordinal()) {
            throw new IllegalArgumentException(
                "You are already on " + currentPlan.name() + " plan. " +
                "Wait for your current subscription to end before resubscribing.");
        }
        if (newPlan.ordinal() < currentPlan.ordinal()) {
            throw new IllegalArgumentException(
                "Cannot switch from " + currentPlan.name() + " to " + newPlan.name() + ". " +
                "Wait for your current subscription to end.");
        }
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