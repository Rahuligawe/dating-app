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
        return createPaymentOrder(userId, plan, yearly, referralCode, null);
    }

    public Map<String, Object> createPaymentOrder(String userId, Plan plan,
                                                  boolean yearly, String referralCode,
                                                  String customerPhone) {
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
        redisTemplate.opsForHash().putAll("order:meta:" + orderId, meta);
        redisTemplate.expire("order:meta:" + orderId, 2, TimeUnit.HOURS);

        return Map.of(
                "orderId",          order.get("orderId"),
                "paymentSessionId", order.get("paymentSessionId"),
                "appId",            order.get("appId"),
                "amount",           finalAmountPaise,
                "baseAmount",       baseAmountPaise,
                "discountPercent",  discountPercent,
                "currency",         "INR",
                "plan",             plan.name(),
                "yearly",           yearly
        );
    }

    // ── Verify + Activate ─────────────────────────────────────────────────────
    @Transactional
    public UserSubscription verifyAndActivate(String userId, Plan plan,
                                              boolean yearly,
                                              String cashfreeOrderId,
                                              String referralCode,
                                              boolean usedGiftedPoints) {
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
        return upgradePlan(userId, plan, cashfreeOrderId, "CASHFREE", yearly);
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
        upgradePlan(userId, plan, orderId, "CASHFREE", yearly);
        redisTemplate.delete("order:meta:" + orderId);
        log.info("Webhook: activated userId={} plan={} yearly={}", userId, plan, yearly);
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