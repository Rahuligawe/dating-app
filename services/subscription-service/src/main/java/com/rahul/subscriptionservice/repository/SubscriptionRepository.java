package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository
        extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserId(String userId);
}