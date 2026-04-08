package com.rahul.radarservice.repository;

import com.rahul.radarservice.entity.RadarPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RadarRepository
        extends JpaRepository<RadarPost, String> {

    @Query("""
        SELECT r FROM RadarPost r
        WHERE r.isActive = true
          AND r.expiryTime > :now
          AND r.userId != :userId
        ORDER BY r.createdAt DESC
    """)
    List<RadarPost> findActiveRadarPosts(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RadarPost r SET r.isActive = false " +
            "WHERE r.userId = :userId AND r.isActive = true")
    void deactivateForUser(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE RadarPost r SET r.isActive = false " +
            "WHERE r.isActive = true AND r.expiryTime <= :now")
    void expireOldPosts(@Param("now") LocalDateTime now);
}