package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.SubscriptionPurchaseHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionPurchaseHistoryRepository extends JpaRepository<SubscriptionPurchaseHistory, Long> {
    List<SubscriptionPurchaseHistory> findByUserIdOrderByPurchasedAtDesc(String userId);
}
