package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.PointsWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PointsWalletRepository extends JpaRepository<PointsWallet, String> {
    Optional<PointsWallet> findByUserId(String userId);
}