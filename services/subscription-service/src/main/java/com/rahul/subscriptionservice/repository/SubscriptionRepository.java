package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.UserSubscription;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository
        extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserId(String userId);

    Optional<UserSubscription> findByCashfreeSubscriptionId(String cashfreeSubscriptionId);

    List<UserSubscription> findByPlanNotAndEndDateBeforeAndIsActiveTrue(
            Plan plan, LocalDateTime now);
}