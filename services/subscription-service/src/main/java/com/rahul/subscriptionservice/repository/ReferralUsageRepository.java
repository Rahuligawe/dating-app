package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.ReferralUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReferralUsageRepository extends JpaRepository<ReferralUsage, Long> {
    boolean existsByCodeAndBuyerUserId(String code, String buyerUserId);
    List<ReferralUsage> findByOwnerUserId(String ownerUserId);
    List<ReferralUsage> findByBuyerUserId(String buyerUserId);
}