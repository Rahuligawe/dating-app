package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.SubscriptionPlan;
import com.rahul.subscriptionservice.entity.UserSubscription.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Plan> {
}