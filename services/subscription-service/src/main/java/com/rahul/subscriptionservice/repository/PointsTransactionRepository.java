package com.rahul.subscriptionservice.repository;

import com.rahul.subscriptionservice.entity.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {
    List<PointsTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
}