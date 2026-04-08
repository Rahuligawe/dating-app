package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, String> {
    Optional<ReferralCode> findByCode(String code);
    Optional<ReferralCode> findByOwnerUserId(String ownerUserId);
}