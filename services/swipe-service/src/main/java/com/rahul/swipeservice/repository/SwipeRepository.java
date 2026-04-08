package com.rahul.swipeservice.repository;

import com.rahul.swipeservice.entity.Swipe;
import com.rahul.swipeservice.entity.Swipe.SwipeAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SwipeRepository extends JpaRepository<Swipe, Long> {

    boolean existsByFromUserIdAndToUserId(
            String fromUserId, String toUserId);

    java.util.Optional<Swipe> findByFromUserIdAndToUserId(
            String fromUserId, String toUserId);

    boolean existsByFromUserIdAndToUserIdAndAction(
            String fromUserId, String toUserId, SwipeAction action);

    List<Swipe> findByFromUserIdAndAction(
            String fromUserId, SwipeAction action);

    // [Fix] "disliked" method — fromUserId field hai
    default List<Swipe> findByFromUserId_Disliked(String userId) {
        return findByFromUserIdAndAction(userId, SwipeAction.DISLIKE);
    }

    // People who liked me but I haven't responded yet
    @Query("""
                SELECT s FROM Swipe s
                WHERE s.toUserId = :userId
                  AND s.action IN ('LIKE', 'SUPER_LIKE')
                  AND NOT EXISTS (
                      SELECT s2 FROM Swipe s2
                      WHERE s2.fromUserId = :userId
                        AND s2.toUserId = s.fromUserId
                  )
            """)
    List<Swipe> findPendingLikesForUser(@Param("userId") String userId);

    // ── New — Profile Screen count ───────────────────────────────────────
    // [Fix] Entity field = fromUserId (not swiperId)
    long countByFromUserId(String fromUserId);

    // ── New — My Likes (jinhe maine LIKE kiya) ───────────────────────────
    // [Fix] Direction nahi hai entity mein — action use karo
    List<Swipe> findByFromUserIdAndActionOrderByCreatedAtDesc(
            String fromUserId, SwipeAction action);

    // ── New — Liked Me (jinhone mujhe LIKE kiya) ───────────────────────────
    // [Fix] Entity field = toUserId (not swipedId)
    List<Swipe> findByToUserIdAndActionOrderByCreatedAtDesc(
            String toUserId, SwipeAction action);

    // [Fix] Sirf count — jinne mujhe like kiya
    long countByToUserIdAndAction(String toUserId, SwipeAction action);

    //long countByToUserIdAndDirection(String toUserId, String direction);

}