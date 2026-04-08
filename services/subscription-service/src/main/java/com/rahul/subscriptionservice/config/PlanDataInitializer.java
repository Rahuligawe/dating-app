package com.rahul.subscriptionservice.config;

import com.rahul.subscriptionservice.entity.SubscriptionPlan;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import com.rahul.subscriptionservice.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanDataInitializer implements CommandLineRunner {

    private final SubscriptionPlanRepository planRepo;

    @Override
    public void run(String... args) {
        insertIfAbsent(SubscriptionPlan.builder()
                .plan(Plan.FREE)
                .dailySwipes(50)
                .unlimitedSwipes(false)
                .seeWhoLikedYou(false)
                .superLikesPerDay(0)
                .hasAds(true)
                .readReceipts(false)
                .profileBoostsPerWeek(0)
                .aiCompatibilityScore(false)
                .travelVisibilityBoost(false)
                .topProfileRanking(false)
                .maxPhotos(5)
                .priceMonthly(0)
                .priceYearly(0)
                .build());

        insertIfAbsent(SubscriptionPlan.builder()
                .plan(Plan.PREMIUM)
                .dailySwipes(0)            // unlimited
                .unlimitedSwipes(true)
                .seeWhoLikedYou(true)
                .superLikesPerDay(5)
                .hasAds(false)
                .readReceipts(true)
                .profileBoostsPerWeek(1)
                .aiCompatibilityScore(false)
                .travelVisibilityBoost(false)
                .topProfileRanking(false)
                .maxPhotos(10)
                .priceMonthly(99)
                .priceYearly(699)
                .build());

        insertIfAbsent(SubscriptionPlan.builder()
                .plan(Plan.ULTRA)
                .dailySwipes(0)            // unlimited
                .unlimitedSwipes(true)
                .seeWhoLikedYou(true)
                .superLikesPerDay(-1)      // unlimited
                .hasAds(false)
                .readReceipts(true)
                .profileBoostsPerWeek(10)
                .aiCompatibilityScore(true)
                .travelVisibilityBoost(true)
                .topProfileRanking(true)
                .maxPhotos(20)
                .priceMonthly(299)
                .priceYearly(1999)
                .build());

        log.info("Subscription plans initialized in DB ✅");
    }

    private void insertIfAbsent(SubscriptionPlan plan) {
        // [Key] Already exist karta hai to update mat karo — DB changes preserve rahenge
        if (!planRepo.existsById(plan.getPlan())) {
            planRepo.save(plan);
            log.info("Inserted plan: {}", plan.getPlan());
        } else {
            log.info("Plan already exists, skipping: {}", plan.getPlan());
        }
    }
}