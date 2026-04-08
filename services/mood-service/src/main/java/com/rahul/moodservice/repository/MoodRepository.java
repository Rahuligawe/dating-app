package com.rahul.moodservice.repository;

import com.rahul.moodservice.entity.MoodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MoodRepository
        extends JpaRepository<MoodStatus, String> {

    // Get active moods for feed (not mine, not expired)
    @Query("""
        SELECT m FROM MoodStatus m
        WHERE m.isActive = true
          AND m.expiryTime > :now
          AND m.userId != :userId
        ORDER BY m.createdAt DESC
    """)
    List<MoodStatus> findActiveMoodsForFeed(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    // Get my current active mood
    @Query("""
        SELECT m FROM MoodStatus m
        WHERE m.userId = :userId
          AND m.isActive = true
          AND m.expiryTime > :now
    """)
    Optional<MoodStatus> findActiveByUserId(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now);

    // Deactivate all moods for a user (before posting new one)
    @Modifying
    @Query("UPDATE MoodStatus m SET m.isActive = false " +
            "WHERE m.userId = :userId AND m.isActive = true")
    void deactivateUserMoods(@Param("userId") String userId);

    // Auto expire moods past their expiry time
    @Modifying
    @Query("UPDATE MoodStatus m SET m.isActive = false " +
            "WHERE m.isActive = true AND m.expiryTime <= :now")
    int expireOldMoods(@Param("now") LocalDateTime now);

    // Get all moods for a specific user (history), newest first
    List<MoodStatus> findByUserIdOrderByCreatedAtDesc(String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MoodStatus m SET m.likeCount = m.likeCount + 1 " +
            "WHERE m.id = :moodId")
    void incrementLikes(@Param("moodId") String moodId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MoodStatus m SET m.likeCount = GREATEST(m.likeCount - 1, 0) " +
            "WHERE m.id = :moodId")
    void decrementLikes(@Param("moodId") String moodId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MoodStatus m SET m.dislikeCount = m.dislikeCount + 1 " +
            "WHERE m.id = :moodId")
    void incrementDislikes(@Param("moodId") String moodId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MoodStatus m SET m.dislikeCount = GREATEST(m.dislikeCount - 1, 0) " +
            "WHERE m.id = :moodId")
    void decrementDislikes(@Param("moodId") String moodId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MoodStatus m " +
            "SET m.commentCount = m.commentCount + 1 " +
            "WHERE m.id = :moodId")
    void incrementComments(@Param("moodId") String moodId);

}