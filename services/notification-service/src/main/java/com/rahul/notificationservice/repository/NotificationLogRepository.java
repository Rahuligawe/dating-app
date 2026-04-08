package com.rahul.notificationservice.repository;

import com.rahul.notificationservice.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogRepository
        extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByUserIdOrderByCreatedAtDesc(
            String userId);

    long countByUserIdAndIsReadFalse(String userId);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.isRead = true " +
            "WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsRead(@Param("userId") String userId);
}