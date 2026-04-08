package com.rahul.subscriptionservice.controller;

import com.rahul.subscriptionservice.entity.*;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import com.rahul.subscriptionservice.service.ReferralService;
import com.rahul.subscriptionservice.service.SubscriptionService;
import com.rahul.subscriptionservice.service.SubscriptionService.PlanFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final ReferralService     referralService;

    // ── Existing endpoints ────────────────────────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<UserSubscription> getMySubscription(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(subscriptionService.getSubscription(userId));
    }

    // Returns plan + endDate + daysLeft — used by Android SettingsActivity
    @GetMapping("/my/status")
    public ResponseEntity<Map<String, Object>> getMyStatus(
            @RequestHeader("X-User-Id") String userId) {
        UserSubscription sub = subscriptionService.getSubscription(userId);
        Map<String, Object> res = new HashMap<>();
        res.put("plan",     sub.getPlan().name());
        res.put("isActive", sub.getIsActive());
        res.put("endDate",  sub.getEndDate() != null ? sub.getEndDate().toString() : null);
        long daysLeft = (sub.getEndDate() != null && sub.getPlan() != UserSubscription.Plan.FREE)
                ? ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getEndDate())
                : 0;
        res.put("daysLeft", Math.max(0, daysLeft));
        return ResponseEntity.ok(res);
    }

    @GetMapping("/plans/all")
    public ResponseEntity<List<PlanFeatures>> getAllPlans() {
        return ResponseEntity.ok(
                Arrays.stream(new Plan[]{Plan.FREE, Plan.PREMIUM, Plan.ULTRA})
                        .map(subscriptionService::getPlanFeatures).toList());
    }

    @GetMapping("/plans/{plan}/features")
    public ResponseEntity<PlanFeatures> getPlanFeatures(@PathVariable String plan) {
        return ResponseEntity.ok(
                subscriptionService.getPlanFeatures(Plan.valueOf(plan.toUpperCase())));
    }

    // ── Payment Flow ──────────────────────────────────────────────────────────

    // Step 1: Create Razorpay order (call this first from Android)
    /*@PostMapping("/order/create")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        Plan   plan         = Plan.valueOf(((String) body.get("plan")).toUpperCase());
        boolean yearly      = Boolean.TRUE.equals(body.get("yearly"));
        String referralCode = (String) body.get("referralCode");
        return ResponseEntity.ok(
                subscriptionService.createPaymentOrder(userId, plan, yearly, referralCode));
    }*/

    @PostMapping("/order/create")
    public ResponseEntity<?> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        try {
            log.info("Create order request: userId={}, body={}", userId, body);

            String planStr = (String) body.get("plan");
            if (planStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "plan is required"));
            }

            Plan plan;
            try {
                plan = Plan.valueOf(planStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid plan: " + planStr + ". Valid: FREE, PREMIUM, ULTRA"));
            }

            boolean yearly = Boolean.TRUE.equals(body.get("yearly"));
            String referralCode = (String) body.get("referralCode");

            Map<String, Object> result = subscriptionService
                    .createPaymentOrder(userId, plan, yearly, referralCode);

            log.info("Order created: {}", result);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Create order failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Step 2: Verify payment + activate (call after Razorpay success)
    @PostMapping("/verify")
    public ResponseEntity<UserSubscription> verifyPayment(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(subscriptionService.verifyAndActivate(
                userId,
                Plan.valueOf(body.get("plan").toUpperCase()),
                Boolean.parseBoolean(body.get("yearly")),
                body.get("razorpayOrderId"),
                body.get("razorpayPaymentId"),
                body.get("razorpaySignature"),
                body.get("referralCode"),
                Boolean.parseBoolean(body.getOrDefault("usedGiftedPoints", "false"))
        ));
    }

    // Purchase via points (no Razorpay)
    @PostMapping("/purchase/points")
    public ResponseEntity<UserSubscription> purchaseViaPoints(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                subscriptionService.activateViaPoints(userId,
                        Plan.valueOf(body.get("plan").toUpperCase())));
    }

    // ── Referral Endpoints ────────────────────────────────────────────────────

    @GetMapping("/referral/my-code")
    public ResponseEntity<ReferralCode> getMyReferralCode(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(referralService.getMyCode(userId));
    }

    @GetMapping("/referral/validate/{code}")
    public ResponseEntity<Map<String, Object>> validateCode(
            @PathVariable String code,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(referralService.validateCode(code, userId));
    }

    @GetMapping("/referral/user-by-code/{code}")
    public ResponseEntity<Map<String, Object>> getUserByReferralCode(@PathVariable String code) {
        return referralService.getUserIdByCode(code.toUpperCase())
                .map(userId -> ResponseEntity.ok(Map.<String, Object>of("userId", userId, "found", true)))
                .orElse(ResponseEntity.ok(Map.of("userId", "", "found", false)));
    }

    // ── Points Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/points/wallet")
    public ResponseEntity<PointsWallet> getWallet(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(referralService.getWallet(userId));
    }

    @GetMapping("/points/transactions")
    public ResponseEntity<List<PointsTransaction>> getTransactions(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(referralService.getTransactions(userId));
    }

    @PostMapping("/points/gift")
    public ResponseEntity<Map<String, Object>> giftPoints(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(referralService.giftPoints(
                userId,
                (String) body.get("toUserId"),
                ((Number) body.get("amount")).doubleValue()
        ));
    }

    @PostMapping("/points/redeem/cash")
    public ResponseEntity<Map<String, Object>> redeemCash(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(referralService.redeemForCash(
                userId,
                ((Number) body.get("amount")).doubleValue(),
                (String) body.get("upiId")
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "subscription-service"));
    }
}